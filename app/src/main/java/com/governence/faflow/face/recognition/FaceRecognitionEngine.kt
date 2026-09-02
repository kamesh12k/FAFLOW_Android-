package com.governence.faflow.face.recognition

import android.graphics.Bitmap
import com.governence.faflow.face.FaceEmbedder
import com.governence.faflow.face.FaceMatcher
import com.governence.faflow.face.alignment.FaceAligner
import com.governence.faflow.face.alignment.UmeyamaFaceAligner
import com.governence.faflow.face.embedding.FaceRecognitionConfig
import com.governence.faflow.face.enrollment.FaceEnrollmentRepository
import com.governence.faflow.face.matching.CosineFaceMatcher
import com.governence.faflow.face.model.FaceDetectionResult
import com.governence.faflow.face.model.StaffBiometricVerificationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * End-to-end on-device face recognition pipeline coordinator.
 * Orchestrates: Detection Validation -> 5-Point Umeyama Alignment -> ArcFace Embedding -> Cosine Verification.
 */
class FaceRecognitionEngine(
    private val aligner: FaceAligner = UmeyamaFaceAligner(),
    private val embedder: FaceEmbedder,
    private val matcher: FaceMatcher = CosineFaceMatcher(),
    private val enrollmentRepository: FaceEnrollmentRepository,
    private val config: FaceRecognitionConfig = FaceRecognitionConfig.DEFAULT
) {

    /**
     * Executes 1-to-1 biometric identity verification against enrolled staff template.
     */
    suspend fun verifyStaffIdentity(
        sourceBitmap: Bitmap,
        detection: FaceDetectionResult,
        staffId: String
    ): StaffBiometricVerificationState = withContext(Dispatchers.Default) {
        // 1. Verify Local Staff Enrollment Exists
        val enrollment = enrollmentRepository.getEnrollment(staffId)
        if (enrollment == null) {
            return@withContext StaffBiometricVerificationState.NoEnrollment(staffId)
        }

        // 2. Validate Detection Landmarks
        val landmarks = detection.landmarks
        if (landmarks == null) {
            return@withContext StaffBiometricVerificationState.VerificationFailed(
                similarity = 0f,
                threshold = config.similarityThreshold,
                reason = "Facial landmarks missing from detection result"
            )
        }

        // 3. 5-Point Umeyama Canonical Alignment
        val alignmentResult = aligner.align(sourceBitmap, landmarks)
        if (!alignmentResult.isValidGeometry || alignmentResult.alignedBitmap == null) {
            return@withContext StaffBiometricVerificationState.VerificationFailed(
                similarity = 0f,
                threshold = config.similarityThreshold,
                reason = alignmentResult.errorMessage ?: "Failed to align face landmarks"
            )
        }

        // 4. ArcFace Feature Embedding Extraction
        val liveEmbedding = try {
            embedder.extractEmbedding(alignmentResult.alignedBitmap)
        } catch (e: Exception) {
            return@withContext StaffBiometricVerificationState.Unavailable(
                reason = "Feature extractor error: ${e.localizedMessage}"
            )
        }

        // 5. Cosine Similarity Verification
        val similarity = matcher.computeCosineSimilarity(liveEmbedding, enrollment.embedding)
        val isVerified = similarity >= config.similarityThreshold

        return@withContext if (isVerified) {
            StaffBiometricVerificationState.Verified(
                staffId = staffId,
                similarity = similarity,
                threshold = config.similarityThreshold
            )
        } else {
            StaffBiometricVerificationState.VerificationFailed(
                similarity = similarity,
                threshold = config.similarityThreshold,
                reason = "Biometric mismatch: similarity score ${"%.2f".format(similarity)} below threshold ${config.similarityThreshold}"
            )
        }
    }

    /**
     * Enrolls a new staff biometric profile with explicit user confirmation.
     */
    suspend fun enrollStaff(
        sourceBitmap: Bitmap,
        detection: FaceDetectionResult,
        staffId: String,
        staffName: String
    ): Result<FloatArray> = withContext(Dispatchers.Default) {
        val landmarks = detection.landmarks
            ?: return@withContext Result.failure(IllegalArgumentException("Detection landmarks are missing"))

        val alignmentResult = aligner.align(sourceBitmap, landmarks)
        if (!alignmentResult.isValidGeometry || alignmentResult.alignedBitmap == null) {
            return@withContext Result.failure(
                IllegalArgumentException(alignmentResult.errorMessage ?: "Landmark alignment failed")
            )
        }

        val embedding = try {
            embedder.extractEmbedding(alignmentResult.alignedBitmap)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val saved = enrollmentRepository.saveEnrollment(
            staffId = staffId,
            staffName = staffName,
            embedding = embedding,
            modelVersion = config.modelVersion,
            alignmentVersion = config.alignmentVersion
        )

        if (saved) {
            Result.success(embedding)
        } else {
            Result.failure(IllegalStateException("Failed to securely store biometric enrollment"))
        }
    }
}
