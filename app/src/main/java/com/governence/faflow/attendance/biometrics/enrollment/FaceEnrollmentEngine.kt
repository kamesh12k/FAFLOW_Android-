package com.governence.faflow.attendance.biometrics.enrollment

import android.graphics.Bitmap
import com.governence.faflow.attendance.biometrics.FaceMatcher
import com.governence.faflow.attendance.biometrics.embedding.MobileFaceNetEmbedder
import com.governence.faflow.attendance.biometrics.model.FaceDetectionResult
import kotlin.math.abs

/**
 * Ordered multi-pose capture targets for high-reliability biometric enrollment.
 */
enum class EnrollmentPoseTarget(
    val id: Int,
    val title: String,
    val shortLabel: String,
    val prompt: String,
    val yawMin: Float,
    val yawMax: Float
) {
    FRONTAL(
        id = 1,
        title = "Frontal View",
        shortLabel = "Center",
        prompt = "Look directly at the camera",
        yawMin = -7f,
        yawMax = 7f
    ),
    LEFT_ANGLE(
        id = 2,
        title = "Turn Head Left",
        shortLabel = "Left",
        prompt = "Turn head slightly to the left (←)",
        yawMin = -35f,
        yawMax = -4.5f
    ),
    RIGHT_ANGLE(
        id = 3,
        title = "Turn Head Right",
        shortLabel = "Right",
        prompt = "Turn head slightly to the right (→)",
        yawMin = 4.5f,
        yawMax = 35f
    );

    fun isPoseSatisfied(yawAngle: Float, rollAngle: Float): Boolean {
        if (abs(rollAngle) > 26f) return false
        return yawAngle in yawMin..yawMax
    }
}

/**
 * Captured biometric sample for a specific pose.
 */
data class PoseCapture(
    val target: EnrollmentPoseTarget,
    val bitmap: Bitmap? = null,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PoseCapture) return false
        return target == other.target && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = target.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

/**
 * Result of evaluating live detection against the current target pose.
 */
sealed interface PoseEvaluationResult {
    data class ValidPose(
        val yaw: Float,
        val prompt: String = "Perfect! Hold still..."
    ) : PoseEvaluationResult

    data class AdjustPose(
        val yaw: Float,
        val guidance: String
    ) : PoseEvaluationResult

    data class QualityIssue(
        val reason: String
    ) : PoseEvaluationResult
}

/**
 * Result of cross-sample biometric identity validation and master profile generation.
 */
sealed interface EnrollmentValidationResult {
    data class Success(
        val masterEmbedding: FloatArray,
        val templates: List<FloatArray>,
        val crossSimilarities: List<Float>
    ) : EnrollmentValidationResult

    data class InconsistentIdentity(
        val lowestSimilarity: Float,
        val requiredThreshold: Float,
        val reason: String
    ) : EnrollmentValidationResult
}

/**
 * Domain engine managing pose classification, stability gating, and cross-consistency validation
 * for accurate multi-pose biometric registration.
 */
class FaceEnrollmentEngine(
    val requiredHoldFrames: Int = 2,
    val minCrossSimilarity: Float = 0.42f
) {

    /**
     * Evaluates a detected face against the requested pose target.
     */
    fun evaluatePose(
        detection: FaceDetectionResult,
        target: EnrollmentPoseTarget
    ): PoseEvaluationResult {
        val q = detection.quality

        // 1. Lighting Gate
        if (q.brightnessScore < 0.20f) {
            return PoseEvaluationResult.QualityIssue("Lighting is too dark. Move to a well-lit area.")
        }

        // 2. Head Tilt (Roll) Gate
        if (abs(q.rollAngle) > 26f) {
            return PoseEvaluationResult.AdjustPose(
                yaw = q.yawAngle,
                guidance = "Keep your head level (avoid tilting)"
            )
        }

        // 3. Pose-specific yaw evaluation
        // In mirrored front camera view:
        // Left head turn -> nose is to the left of eye center -> negative yaw
        // Right head turn -> nose is to the right of eye center -> positive yaw
        val yaw = q.yawAngle
        return when (target) {
            EnrollmentPoseTarget.FRONTAL -> {
                if (yaw > 7f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn slightly left towards center")
                } else if (yaw < -7f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn slightly right towards center")
                } else {
                    PoseEvaluationResult.ValidPose(yaw)
                }
            }
            EnrollmentPoseTarget.LEFT_ANGLE -> {
                if (yaw > -4.5f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn head a little more to the left (←)")
                } else if (yaw < -35f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn back slightly towards center")
                } else {
                    PoseEvaluationResult.ValidPose(yaw)
                }
            }
            EnrollmentPoseTarget.RIGHT_ANGLE -> {
                if (yaw < 4.5f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn head a little more to the right (→)")
                } else if (yaw > 35f) {
                    PoseEvaluationResult.AdjustPose(yaw, "Turn back slightly towards center")
                } else {
                    PoseEvaluationResult.ValidPose(yaw)
                }
            }
        }
    }

    /**
     * Verifies that all 3 captured samples belong to the same person across poses (cross-sample consistency),
     * and computes the L2-normalized master composite embedding.
     */
    fun validateAndConsolidate(
        captures: List<PoseCapture>,
        matcher: FaceMatcher
    ): EnrollmentValidationResult {
        val frontal = captures.firstOrNull { it.target == EnrollmentPoseTarget.FRONTAL }
        val left = captures.firstOrNull { it.target == EnrollmentPoseTarget.LEFT_ANGLE }
        val right = captures.firstOrNull { it.target == EnrollmentPoseTarget.RIGHT_ANGLE }

        if (frontal == null || left == null || right == null) {
            return EnrollmentValidationResult.InconsistentIdentity(
                lowestSimilarity = 0f,
                requiredThreshold = minCrossSimilarity,
                reason = "All 3 poses (Center, Left, Right) must be captured before validation."
            )
        }

        val eCenter = frontal.embedding
        val eLeft = left.embedding
        val eRight = right.embedding

        val sCenterLeft = matcher.computeCosineSimilarity(eCenter, eLeft)
        val sCenterRight = matcher.computeCosineSimilarity(eCenter, eRight)
        val sLeftRight = matcher.computeCosineSimilarity(eLeft, eRight)

        val lowestAngledMatch = minOf(sCenterLeft, sCenterRight)
        val effectiveThreshold = minOf(minCrossSimilarity, 0.40f)

        if (lowestAngledMatch < effectiveThreshold) {
            return EnrollmentValidationResult.InconsistentIdentity(
                lowestSimilarity = lowestAngledMatch,
                requiredThreshold = effectiveThreshold,
                reason = "Cross-pose similarity (${"%.2f".format(lowestAngledMatch)}) fell below required threshold (${"%.2f".format(effectiveThreshold)}). Please re-enroll in stable lighting."
            )
        }

        // Master composite calculation: mean vector of 3 poses + L2-normalization
        val master = computeMasterEmbedding(listOf(eCenter, eLeft, eRight))

        return EnrollmentValidationResult.Success(
            masterEmbedding = master,
            templates = listOf(eCenter, eLeft, eRight),
            crossSimilarities = listOf(sCenterLeft, sCenterRight, sLeftRight)
        )
    }

    /**
     * Averages multiple 512-D embedding vectors and applies L2-normalization.
     */
    fun computeMasterEmbedding(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Embeddings list cannot be empty" }
        val dim = embeddings.first().size
        val accum = FloatArray(dim)

        for (emb in embeddings) {
            for (i in 0 until dim) {
                accum[i] += emb[i]
            }
        }

        val count = embeddings.size.toFloat()
        for (i in 0 until dim) {
            accum[i] /= count
        }

        return MobileFaceNetEmbedder.l2Normalize(accum)
    }
}
