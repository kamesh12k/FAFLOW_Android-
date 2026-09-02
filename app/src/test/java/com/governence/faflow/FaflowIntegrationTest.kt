package com.governence.faflow

import android.graphics.ImageFormat
import com.governence.faflow.camera.CameraFrame
import com.governence.faflow.camera.CameraFrameProcessor
import com.governence.faflow.camera.CameraLens
import com.governence.faflow.camera.CameraState
import com.governence.faflow.camera.FrameProcessResult
import com.governence.faflow.core.network.CreditBalanceOutDto
import com.governence.faflow.core.network.CreditTransactionOutDto
import com.governence.faflow.core.network.DayOrderResolveDto
import com.governence.faflow.core.network.LeaveOutDto
import com.governence.faflow.core.network.NetworkResult
import com.governence.faflow.core.network.NotificationOutDto
import com.governence.faflow.core.network.SubstitutionPreferenceOutDto
import com.governence.faflow.core.network.TeacherTodaySummaryDto
import com.governence.faflow.core.network.TimetableSlotOutDto
import com.governence.faflow.core.network.UserOutDto
import com.governence.faflow.domain.model.CreditTransaction
import com.governence.faflow.domain.model.LeaveRequest
import com.governence.faflow.domain.model.LeaveStatus
import com.governence.faflow.domain.model.StaffMember
import com.governence.faflow.domain.model.TimetableSlot
import com.governence.faflow.face.ModelInfo
import com.governence.faflow.face.ModelTask
import com.governence.faflow.face.ModelState
import com.governence.faflow.face.alignment.FaceAlignmentConfig
import com.governence.faflow.face.alignment.UmeyamaFaceAligner
import com.governence.faflow.face.embedding.ArcFaceEmbedder
import com.governence.faflow.face.embedding.FaceRecognitionConfig
import com.governence.faflow.face.enrollment.StaffFaceEnrollment
import com.governence.faflow.face.matching.CosineFaceMatcher
import com.governence.faflow.face.model.FaceBox
import com.governence.faflow.face.model.FaceDetectionResult
import com.governence.faflow.face.model.FaceLandmarks
import com.governence.faflow.face.model.FacePoint
import com.governence.faflow.face.model.StaffBiometricVerificationState
import com.governence.faflow.face.scrfd.LetterboxInfo
import com.governence.faflow.face.scrfd.ScrfdCandidate
import com.governence.faflow.face.scrfd.ScrfdDecoder
import com.governence.faflow.face.scrfd.ScrfdPostprocessor
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.GeoPoint
import com.governence.faflow.location.GeofenceMathEngine
import com.governence.faflow.location.GeofenceType
import com.governence.faflow.location.GeofenceValidator
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.location.StaffLiveLocation
import com.governence.faflow.ui.viewmodels.FaceDetectionUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

class FaflowIntegrationTest {

    // ---------- Milestone 7: Face Alignment & On-Device Recognition Tests ----------

    @Test
    fun testUmeyamaIdentityTransform() {
        val aligner = UmeyamaFaceAligner()
        val canonical = FaceAlignmentConfig.REFERENCE_LANDMARKS
        val transform = aligner.estimateSimilarityTransform(src = canonical, dst = canonical)

        assertNotNull("Identity transform must not be null", transform)
        // Scale = 1.0, Rotation = 0.0, Translation = 0.0
        assertEquals(1.0f, transform!!.scale, 0.01f)
        assertEquals(1.0f, transform.a, 0.01f) // scale * cos(0)
        assertEquals(0.0f, transform.b, 0.01f) // -scale * sin(0)
        assertEquals(0.0f, transform.tx, 0.01f) // tx
        assertEquals(0.0f, transform.c, 0.01f) // scale * sin(0)
        assertEquals(1.0f, transform.d, 0.01f) // scale * cos(0)
        assertEquals(0.0f, transform.ty, 0.01f) // ty
    }

    @Test
    fun testUmeyamaTranslation() {
        val aligner = UmeyamaFaceAligner()
        val canonical = FaceAlignmentConfig.REFERENCE_LANDMARKS
        val translated = canonical.map { FacePoint(it.x + 20f, it.y + 30f) }

        // Mapping translated -> canonical should have tx = -20, ty = -30
        val transform = aligner.estimateSimilarityTransform(src = translated, dst = canonical)
        assertNotNull(transform)

        assertEquals(1.0f, transform!!.scale, 0.01f)
        assertEquals(1.0f, transform.a, 0.01f)
        assertEquals(1.0f, transform.d, 0.01f)
        assertEquals(-20.0f, transform.tx, 0.1f)
        assertEquals(-30.0f, transform.ty, 0.1f)
    }

    @Test
    fun testUmeyamaScale() {
        val aligner = UmeyamaFaceAligner()
        val canonical = FaceAlignmentConfig.REFERENCE_LANDMARKS
        val scaled = canonical.map { FacePoint(it.x * 2.0f, it.y * 2.0f) }

        // Mapping scaled (2x) -> canonical (1x) should have scale factor = 0.5
        val transform = aligner.estimateSimilarityTransform(src = scaled, dst = canonical)
        assertNotNull(transform)

        assertEquals(0.5f, transform!!.scale, 0.01f)
        assertEquals(0.5f, transform.a, 0.01f)
        assertEquals(0.5f, transform.d, 0.01f)
    }

    @Test
    fun testFivePointLandmarkOrderingValidation() {
        val aligner = UmeyamaFaceAligner()

        // Valid landmarks
        val validLandmarks = FaceLandmarks(
            leftEye = FacePoint(180f, 150f),
            rightEye = FacePoint(240f, 150f),
            nose = FacePoint(210f, 180f),
            leftMouth = FacePoint(190f, 210f),
            rightMouth = FacePoint(230f, 210f)
        )
        assertNull("Valid landmarks should return null error", aligner.validateLandmarks(validLandmarks, 640, 480))

        // Inverted eye order (left eye x > right eye x)
        val invertedEyes = validLandmarks.copy(
            leftEye = FacePoint(240f, 150f),
            rightEye = FacePoint(180f, 150f)
        )
        assertNotNull("Inverted eyes must fail validation", aligner.validateLandmarks(invertedEyes, 640, 480))

        // Nose above eyes
        val invertedNose = validLandmarks.copy(nose = FacePoint(210f, 120f))
        assertNotNull("Nose above eyes must fail validation", aligner.validateLandmarks(invertedNose, 640, 480))

        // Mouth above nose
        val invertedMouth = validLandmarks.copy(
            leftMouth = FacePoint(190f, 170f),
            rightMouth = FacePoint(230f, 170f)
        )
        assertNotNull("Mouth above nose must fail validation", aligner.validateLandmarks(invertedMouth, 640, 480))
    }

    @Test
    fun testEmbeddingNormalization() {
        val rawVector = floatArrayOf(3.0f, 4.0f, 0.0f, 0.0f) // Magnitude = 5.0
        val normalized = ArcFaceEmbedder.l2Normalize(rawVector)

        assertEquals(0.6f, normalized[0], 0.001f)
        assertEquals(0.8f, normalized[1], 0.001f)
        assertEquals(0.0f, normalized[2], 0.001f)

        var norm = 0.0
        for (v in normalized) norm += (v * v).toDouble()
        assertEquals(1.0, sqrt(norm), 0.0001)
    }

    @Test
    fun testZeroVectorNormalization() {
        val zeroVector = FloatArray(512) { 0.0f }
        val normalized = ArcFaceEmbedder.l2Normalize(zeroVector)

        assertEquals(512, normalized.size)
        for (v in normalized) {
            assertEquals(0.0f, v, 0.0f)
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
        }
    }

    @Test
    fun testNaNAndInfinityEmbeddingSanitization() {
        val nanVector = floatArrayOf(1.0f, Float.NaN, 3.0f)
        val sanitizedNan = ArcFaceEmbedder.l2Normalize(nanVector)
        for (v in sanitizedNan) {
            assertEquals(0.0f, v, 0.0f)
        }

        val infVector = floatArrayOf(1.0f, Float.POSITIVE_INFINITY, 3.0f)
        val sanitizedInf = ArcFaceEmbedder.l2Normalize(infVector)
        for (v in sanitizedInf) {
            assertEquals(0.0f, v, 0.0f)
        }
    }

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val matcher = CosineFaceMatcher()
        val vecA = floatArrayOf(0.6f, 0.8f, 0.0f)
        val vecB = floatArrayOf(0.6f, 0.8f, 0.0f)

        val sim = matcher.computeCosineSimilarity(vecA, vecB)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val matcher = CosineFaceMatcher()
        val vecA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecB = floatArrayOf(0.0f, 1.0f, 0.0f)

        val sim = matcher.computeCosineSimilarity(vecA, vecB)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityOppositeVectors() {
        val matcher = CosineFaceMatcher()
        val vecA = floatArrayOf(1.0f, 0.0f)
        val vecB = floatArrayOf(-1.0f, 0.0f)

        val sim = matcher.computeCosineSimilarity(vecA, vecB)
        assertEquals(-1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityDimensionMismatch() {
        val matcher = CosineFaceMatcher()
        val vecA = floatArrayOf(1.0f, 0.0f)
        val vecB = floatArrayOf(1.0f, 0.0f, 0.0f)

        val sim = matcher.computeCosineSimilarity(vecA, vecB)
        assertEquals(0.0f, sim, 0.0f)
    }

    @Test
    fun testFaceMatcherThreshold() {
        val matcher = CosineFaceMatcher(FaceRecognitionConfig(similarityThreshold = 0.60f))
        val live = floatArrayOf(0.7f, 0.714f)
        val enrolled = floatArrayOf(0.7f, 0.714f)

        val result = matcher.verifyOneToOne(live, enrolled)
        assertTrue(result.isMatched)
        assertTrue(result.similarityScore >= 0.60f)

        val different = floatArrayOf(-0.7f, 0.714f)
        val failedResult = matcher.verifyOneToOne(different, enrolled)
        assertFalse(failedResult.isMatched)
    }

    @Test
    fun testStaffFaceEnrollmentDataModel() {
        val enrollment = StaffFaceEnrollment(
            staffId = "42",
            staffName = "Dr. Kamesh V",
            embedding = FloatArray(512) { 0.1f },
            modelVersion = "InsightFace-ArcFace-v1",
            alignmentVersion = "Umeyama-112x112-v1",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val duplicate = enrollment.copy()
        assertEquals(enrollment, duplicate)
        assertEquals(enrollment.hashCode(), duplicate.hashCode())
    }

    @Test
    fun testStaffBiometricVerificationStateMachine() {
        val unavail: StaffBiometricVerificationState = StaffBiometricVerificationState.Unavailable("Model missing")
        assertTrue(unavail is StaffBiometricVerificationState.Unavailable)

        val noEnroll: StaffBiometricVerificationState = StaffBiometricVerificationState.NoEnrollment("42")
        assertTrue(noEnroll is StaffBiometricVerificationState.NoEnrollment)

        val verified: StaffBiometricVerificationState = StaffBiometricVerificationState.Verified("42", 0.88f, 0.60f)
        assertTrue(verified is StaffBiometricVerificationState.Verified)
        assertEquals("42", (verified as StaffBiometricVerificationState.Verified).staffId)
        assertEquals(0.88f, verified.similarity, 0.001f)

        val failed: StaffBiometricVerificationState = StaffBiometricVerificationState.VerificationFailed(0.42f, 0.60f, "Low score")
        assertTrue(failed is StaffBiometricVerificationState.VerificationFailed)
    }

    // ---------- Milestone 6: InsightFace SCRFD Face Detection Tests ----------

    @Test
    fun testScrfdAnchorGenerationCounts() {
        val stride8Anchors = ScrfdDecoder.generateAnchorCenters(inputWidth = 640, inputHeight = 640, stride = 8, numAnchors = 2)
        assertEquals(12800, stride8Anchors.size) // (640/8) * (640/8) * 2 = 80 * 80 * 2 = 12800

        val stride16Anchors = ScrfdDecoder.generateAnchorCenters(inputWidth = 640, inputHeight = 640, stride = 16, numAnchors = 2)
        assertEquals(3200, stride16Anchors.size) // 40 * 40 * 2 = 3200

        val stride32Anchors = ScrfdDecoder.generateAnchorCenters(inputWidth = 640, inputHeight = 640, stride = 32, numAnchors = 2)
        assertEquals(800, stride32Anchors.size) // 20 * 20 * 2 = 800

        val totalAnchors = stride8Anchors.size + stride16Anchors.size + stride32Anchors.size
        assertEquals(16800, totalAnchors)
    }

    @Test
    fun testScrfdBoundingBoxAndLandmarkDecoding() {
        val letterbox = LetterboxInfo(
            scale = 1.0f,
            padX = 0f,
            padY = 0f,
            originalWidth = 640,
            originalHeight = 640,
            rotationApplied = 0
        )

        val scores = floatArrayOf(0.92f)
        val bboxDeltas = floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f) // l, t, r, b = 16px each at stride 8
        val kpsDeltas = floatArrayOf(
            -1.0f, -1.0f, // Left eye
            1.0f, -1.0f,  // Right eye
            0.0f, 0.0f,   // Nose
            -0.8f, 1.0f,  // Left mouth
            0.8f, 1.0f    // Right mouth
        )
        val anchorCenters = listOf(Pair(320f, 240f)) // Center anchor

        val candidates = ScrfdDecoder.decodeStride(
            scores = scores,
            bboxDeltas = bboxDeltas,
            kpsDeltas = kpsDeltas,
            anchorCenters = anchorCenters,
            stride = 8,
            scoreThreshold = 0.50f,
            letterboxInfo = letterbox
        )

        assertEquals(1, candidates.size)
        val cand = candidates.first()
        assertEquals(0.92f, cand.score, 0.001f)

        // Bounding box: cx - l*s = 320 - 16 = 304, cy - t*s = 240 - 16 = 224, etc.
        assertEquals(304f, cand.box.left, 0.1f)
        assertEquals(224f, cand.box.top, 0.1f)
        assertEquals(336f, cand.box.right, 0.1f)
        assertEquals(256f, cand.box.bottom, 0.1f)

        // Landmarks
        assertNotNull(cand.landmarks)
        assertEquals(312f, cand.landmarks!!.leftEye.x, 0.1f) // 320 + (-1 * 8) = 312
        assertEquals(232f, cand.landmarks!!.leftEye.y, 0.1f) // 240 + (-1 * 8) = 232
        assertEquals(328f, cand.landmarks!!.rightEye.x, 0.1f) // 320 + (1 * 8) = 328
        assertEquals(320f, cand.landmarks!!.nose.x, 0.1f)
    }

    @Test
    fun testIoUCalculation() {
        val boxA = FaceBox(left = 100f, top = 100f, right = 200f, bottom = 200f) // 100x100 = 10000
        val boxB = FaceBox(left = 100f, top = 100f, right = 200f, bottom = 200f) // Identical
        val iouSame = ScrfdPostprocessor.calculateIoU(boxA, boxB)
        assertEquals(1.0f, iouSame, 0.001f)

        val boxDisjoint = FaceBox(left = 300f, top = 300f, right = 400f, bottom = 400f)
        val iouDisjoint = ScrfdPostprocessor.calculateIoU(boxA, boxDisjoint)
        assertEquals(0.0f, iouDisjoint, 0.001f)

        val boxPartial = FaceBox(left = 150f, top = 100f, right = 250f, bottom = 200f) // 50% overlap width
        val iouPartial = ScrfdPostprocessor.calculateIoU(boxA, boxPartial)
        assertTrue(iouPartial in 0.30f..0.36f)
    }

    @Test
    fun testNonMaximumSuppressionSuppressesOverlaps() {
        val candidates = listOf(
            ScrfdCandidate(box = FaceBox(100f, 100f, 200f, 200f), score = 0.95f),
            ScrfdCandidate(box = FaceBox(105f, 102f, 202f, 201f), score = 0.85f), // Heavy overlap with first
            ScrfdCandidate(box = FaceBox(400f, 100f, 500f, 200f), score = 0.90f)  // Second distinct face
        )

        val filtered = ScrfdPostprocessor.applyNMS(candidates, iouThreshold = 0.40f)
        assertEquals(2, filtered.size)
        assertEquals(0.95f, filtered[0].score, 0.001f)
        assertEquals(0.90f, filtered[1].score, 0.001f)
    }

    @Test
    fun testQualityAssessmentAndHeadPose() {
        val landmarks = FaceLandmarks(
            leftEye = FacePoint(180f, 150f),
            rightEye = FacePoint(240f, 150f),
            nose = FacePoint(210f, 180f),
            leftMouth = FacePoint(190f, 210f),
            rightMouth = FacePoint(230f, 210f)
        )
        val candidate = ScrfdCandidate(
            box = FaceBox(150f, 100f, 270f, 240f),
            score = 0.94f,
            landmarks = landmarks
        )

        val quality = ScrfdPostprocessor.assessQuality(candidate, frameWidth = 640, frameHeight = 480)
        assertTrue("Frontal face should have isFrontal = true", quality.isFrontal)
        assertTrue("Face width 120px is adequately sized", quality.isAdequatelySized)
        assertTrue("Quality is acceptable for attendance", quality.isAcceptableForEnrollment)
    }

    @Test
    fun testModelManagerLifecycleStates() {
        val uninit: ModelState = ModelState.Uninitialized
        assertTrue(uninit is ModelState.Uninitialized)

        val loading: ModelState = ModelState.Loading(0.5f)
        assertTrue(loading is ModelState.Loading)
        assertEquals(0.5f, (loading as ModelState.Loading).progress, 0.01f)

        val modelInfo = ModelInfo(
            modelId = "test-model",
            modelName = "Test SCRFD",
            version = "1.0",
            task = ModelTask.DETECTION,
            inputShape = intArrayOf(1, 3, 640, 640),
            fileSizeInBytes = 2500000L,
            licenseType = "Evaluation",
            isCommercialPermitted = false
        )
        val ready: ModelState = ModelState.Ready(listOf(modelInfo))
        assertTrue(ready is ModelState.Ready)
        assertEquals(1, (ready as ModelState.Ready).models.size)

        val error: ModelState = ModelState.Error("Asset missing")
        assertTrue(error is ModelState.Error)
        assertEquals("Asset missing", (error as ModelState.Error).message)
    }

    @Test
    fun testCoordinateTransformationAndPreviewMirroring() {
        val frameWidth = 640f
        val faceBox = FaceBox(left = 100f, top = 50f, right = 300f, bottom = 350f)

        // In mirrored selfie preview: left becomes (frameWidth - right)
        val mirroredLeft = frameWidth - faceBox.right
        val mirroredRight = frameWidth - faceBox.left

        assertEquals(340f, mirroredLeft, 0.01f)
        assertEquals(540f, mirroredRight, 0.01f)
        assertEquals(faceBox.width, mirroredRight - mirroredLeft, 0.01f)
    }

    // ---------- Milestone 5: CameraX Pipeline & Frame Processing Tests ----------

    @Test
    fun testCameraStateTransitions() {
        val permState: CameraState = CameraState.PermissionRequired
        assertTrue(permState is CameraState.PermissionRequired)

        val initState: CameraState = CameraState.Initializing
        assertTrue(initState is CameraState.Initializing)

        val readyState: CameraState = CameraState.Ready
        assertTrue(readyState is CameraState.Ready)

        val procState: CameraState = CameraState.Processing(fps = 10.0f, droppedCount = 2L)
        assertTrue(procState is CameraState.Processing)
        assertEquals(10.0f, (procState as CameraState.Processing).fps, 0.01f)
        assertEquals(2L, procState.droppedCount)

        val unavailState: CameraState = CameraState.Unavailable("Front camera not found")
        assertTrue(unavailState is CameraState.Unavailable)
        assertEquals("Front camera not found", (unavailState as CameraState.Unavailable).reason)

        val errorState: CameraState = CameraState.Error("Capture failed", canRetry = true)
        assertTrue(errorState is CameraState.Error)
        assertTrue((errorState as CameraState.Error).canRetry)
    }

    @Test
    fun testCameraFrameMetadataAndRotationHandling() {
        val rotations = listOf(0, 90, 180, 270)

        for (rotation in rotations) {
            val frame = CameraFrame(
                width = 640,
                height = 480,
                rotationDegrees = rotation,
                timestamp = 1000L,
                imageFormat = ImageFormat.YUV_420_888,
                lensFacing = CameraLens.FRONT,
                nv21Bytes = ByteArray(10) { 1 }
            )

            assertEquals(640, frame.width)
            assertEquals(480, frame.height)
            assertEquals(rotation, frame.rotationDegrees)
            assertEquals(CameraLens.FRONT, frame.lensFacing)
            assertEquals(ImageFormat.YUV_420_888, frame.imageFormat)
            assertNotNull(frame.nv21Bytes)
        }
    }

    @Test
    fun testCameraFrameProcessorAsyncExecution() = runBlocking {
        val dummyProcessor = object : CameraFrameProcessor {
            override suspend fun processFrame(frame: CameraFrame): FrameProcessResult {
                return if (frame.width > 0 && frame.height > 0) {
                    FrameProcessResult.FrameReady(
                        frameId = 101L,
                        width = frame.width,
                        height = frame.height,
                        rotationDegrees = frame.rotationDegrees
                    )
                } else {
                    FrameProcessResult.Error("Invalid frame dimensions")
                }
            }
        }

        val testFrame = CameraFrame(
            width = 640,
            height = 480,
            rotationDegrees = 270,
            lensFacing = CameraLens.FRONT
        )

        val result = dummyProcessor.processFrame(testFrame)
        assertTrue(result is FrameProcessResult.FrameReady)
        val ready = result as FrameProcessResult.FrameReady
        assertEquals(101L, ready.frameId)
        assertEquals(640, ready.width)
        assertEquals(480, ready.height)
        assertEquals(270, ready.rotationDegrees)
    }

    @Test
    fun testFrameThrottlingAndConcurrentLock() {
        val maxFps = 10
        val minIntervalMs = 1000L / maxFps
        var lastProcessedTime = -1000L
        val processedCount = AtomicInteger(0)
        val droppedCount = AtomicInteger(0)
        val isProcessing = AtomicBoolean(false)

        fun simulateIncomingFrame(timestamp: Long, isWorkerBusy: Boolean) {
            if (timestamp - lastProcessedTime < minIntervalMs) {
                droppedCount.incrementAndGet()
                return
            }

            if (isWorkerBusy) {
                droppedCount.incrementAndGet()
                return
            }

            lastProcessedTime = timestamp
            processedCount.incrementAndGet()
        }

        simulateIncomingFrame(0L, isWorkerBusy = false)
        assertEquals(1, processedCount.get())
        assertEquals(0, droppedCount.get())

        simulateIncomingFrame(30L, isWorkerBusy = false)
        assertEquals(1, processedCount.get())
        assertEquals(1, droppedCount.get())

        simulateIncomingFrame(120L, isWorkerBusy = true)
        assertEquals(1, processedCount.get())
        assertEquals(2, droppedCount.get())

        simulateIncomingFrame(250L, isWorkerBusy = false)
        assertEquals(2, processedCount.get())
        assertEquals(2, droppedCount.get())
    }

    // ---------- Milestone 4: Comprehensive Geofence & Location Verification Tests ----------

    @Test
    fun testSameCoordinatesReturnsZeroDistance() {
        val lat = 11.016844
        val lon = 76.955833
        val distance = GeofenceMathEngine.calculateDistanceMeters(lat, lon, lat, lon)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun testKnownGeographicDistanceHaversine() {
        val lat1 = 11.016844
        val lon1 = 76.955833
        val lat2 = 11.018000
        val lon2 = 76.956000

        val distance = GeofenceMathEngine.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 130m", distance in 120.0..140.0)
    }

    @Test
    fun testCircleInsideOutsideAndBoundary() {
        val center = GeoPoint(11.016844, 76.955833)
        val radius = 150.0
        val tolerance = 15.0

        val insidePoint = GeoPoint(11.017200, 76.955833)
        val (isInside, isBoundary, distInside) = GeofenceMathEngine.evaluateCircle(insidePoint, center, radius, tolerance)
        assertTrue("Point should be inside", isInside)
        assertFalse("Point 50m away is not on the 150m boundary", isBoundary)
        assertTrue("Distance < 150m", distInside < radius)

        val boundaryPoint = GeoPoint(11.018150, 76.955833)
        val (isInsideBound, isBoundaryTrue, distBound) = GeofenceMathEngine.evaluateCircle(boundaryPoint, center, radius, tolerance)
        assertTrue("Point on boundary is within tolerance", isInsideBound)
        assertTrue("Point should be flagged as boundary", isBoundaryTrue)

        val outsidePoint = GeoPoint(11.021000, 76.955833)
        val (isInsideOut, isBoundaryOut, distOut) = GeofenceMathEngine.evaluateCircle(outsidePoint, center, radius, tolerance)
        assertFalse("Point should be outside", isInsideOut)
        assertFalse("Point is not on boundary", isBoundaryOut)
        assertTrue("Distance > 150m", distOut > radius)
    }

    @Test
    fun testPolygonInsideOutsideAndInvalidVertices() {
        val vertices = listOf(
            GeoPoint(11.017000, 76.956000),
            GeoPoint(11.019000, 76.956000),
            GeoPoint(11.019000, 76.958000),
            GeoPoint(11.017000, 76.958000)
        )

        val pointInside = GeoPoint(11.018000, 76.957000)
        assertTrue("Center point must be inside polygon", GeofenceMathEngine.isInsidePolygon(pointInside, vertices))

        val pointOutside = GeoPoint(11.025000, 76.960000)
        assertFalse("Point must be outside polygon", GeofenceMathEngine.isInsidePolygon(pointOutside, vertices))

        val invalidVertices = listOf(GeoPoint(11.0, 76.0), GeoPoint(11.1, 76.1))
        assertFalse("Polygon with <3 vertices cannot contain points", GeofenceMathEngine.isInsidePolygon(pointInside, invalidVertices))
    }

    @Test
    fun testMultipleGeofencesNearestIdentification() {
        val validator = GeofenceValidator(maxAccuracyThresholdMeters = 30.0f)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Block", GeofenceType.CIRCLE, 11.016844, 76.955833, 100.0),
            CampusGeofence("GEO-2", "Engineering Block", GeofenceType.CIRCLE, 11.025000, 76.965000, 100.0)
        )

        val outsidePoint = StaffLiveLocation(
            latitude = 11.018200,
            longitude = 76.955833,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validate(outsidePoint, geofences)
        assertTrue("Result should be OutsideAllGeofences", result is LocationVerificationResult.OutsideAllGeofences)
        val outside = result as LocationVerificationResult.OutsideAllGeofences
        assertEquals("Main Block", outside.nearestGeofenceName)
    }

    @Test
    fun testMockLocationRejection() {
        val validator = GeofenceValidator()
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val mockedLocation = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracyMeters = 4.0f,
            isMock = true,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validate(mockedLocation, geofences)
        assertTrue("Must detect and reject mock location", result is LocationVerificationResult.MockLocationDetected)
    }

    @Test
    fun testStaleLocationRejection() {
        val validator = GeofenceValidator(maxLocationAgeSeconds = 60L)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val staleLocation = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis() - 120_000L
        )

        val result = validator.validate(staleLocation, geofences)
        assertTrue("Must detect stale GPS data", result is LocationVerificationResult.StaleLocation)
    }

    @Test
    fun testAccuracyInsufficientRejection() {
        val validator = GeofenceValidator(maxAccuracyThresholdMeters = 30.0f)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val inaccurateLocation = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracyMeters = 95.0f,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validate(inaccurateLocation, geofences)
        assertTrue("Must reject poor accuracy", result is LocationVerificationResult.AccuracyInsufficient)
    }

    @Test
    fun testNoActiveGeofencesState() {
        val validator = GeofenceValidator()
        val emptyGeofences = emptyList<CampusGeofence>()

        val loc = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validate(loc, emptyGeofences)
        assertTrue("Must return NoActiveGeofences", result is LocationVerificationResult.NoActiveGeofences)
    }

    // ---------- Milestone 2 & 3 Integration Tests ----------

    @Test
    fun testStaffMemberModelAndUserOutDtoMapping() {
        val userDto = UserOutDto(
            id = 42,
            name = "Dr. Kamesh V",
            email = "kamesh@institution.edu",
            username = "kamesh",
            role = "teacher",
            department = "Computer Science",
            departmentId = 1,
            isActive = true
        )

        val staff = StaffMember(
            id = userDto.id,
            name = userDto.name,
            email = userDto.email ?: "",
            username = userDto.username,
            role = userDto.role,
            departmentId = userDto.departmentId,
            departmentName = userDto.department,
            isActive = userDto.isActive
        )

        assertEquals(42, staff.id)
        assertEquals("Dr. Kamesh V", staff.name)
        assertEquals("teacher", staff.role)
        assertEquals(1, staff.departmentId)
        assertEquals("Computer Science", staff.departmentName)
        assertTrue(staff.isActive)
    }

    @Test
    fun testTimetableMappingAndDayOrderMatrix() {
        val dtos = listOf(
            TimetableSlotOutDto(id = 1, teacherId = 42, subjectId = 10, classId = 5, roomId = 12, dayOrder = 3, periodNumber = 1, subjectName = "Deep Learning", subjectCode = "CS801", className = "III B.Sc CS", classSection = "A", roomNumber = "Room 302"),
            TimetableSlotOutDto(id = 2, teacherId = 42, subjectId = 11, classId = 4, roomId = 8, dayOrder = 3, periodNumber = 3, subjectName = "Operating Systems", subjectCode = "CS402", className = "II B.Sc CS", classSection = "B", roomNumber = "Room 204")
        )

        val domainSlots = dtos.map { dto ->
            TimetableSlot(
                id = dto.id,
                teacherId = dto.teacherId,
                subjectName = dto.subjectName ?: "",
                subjectCode = dto.subjectCode ?: "",
                className = dto.className ?: "",
                section = dto.classSection ?: "",
                roomNumber = dto.roomNumber ?: "",
                dayOrder = dto.dayOrder,
                periodNumber = dto.periodNumber
            )
        }

        val day3Slots = domainSlots.filter { it.dayOrder == 3 }.sortedBy { it.periodNumber }
        assertEquals(2, day3Slots.size)
        assertEquals("Deep Learning", day3Slots[0].subjectName)
        assertEquals(1, day3Slots[0].periodNumber)
    }

    @Test
    fun testLeaveRequestStatusMappingAndEmergencyHandling() {
        val leaveDto = LeaveOutDto(
            id = 101,
            teacherId = 42,
            date = "2026-09-03",
            dayOrder = 4,
            periodNumber = 2,
            reason = "Emergency Medical Attention",
            status = "approved",
            isEmergency = true
        )

        val status = when (leaveDto.status.lowercase()) {
            "approved" -> LeaveStatus.APPROVED
            "rejected" -> LeaveStatus.REJECTED
            "cancelled" -> LeaveStatus.CANCELLED
            else -> LeaveStatus.PENDING
        }

        val leave = LeaveRequest(
            id = leaveDto.id,
            teacherId = leaveDto.teacherId,
            date = leaveDto.date,
            dayOrder = leaveDto.dayOrder,
            periodNumber = leaveDto.periodNumber,
            reason = leaveDto.reason,
            status = status,
            isEmergency = leaveDto.isEmergency
        )

        assertEquals(LeaveStatus.APPROVED, leave.status)
        assertTrue(leave.isEmergency)
    }

    @Test
    fun testNetworkResultStateMachine() {
        val loading: NetworkResult<Nothing> = NetworkResult.Loading
        assertTrue(loading is NetworkResult.Loading)

        val success: NetworkResult<String> = NetworkResult.Success("SUCCESS_PAYLOAD")
        assertTrue(success is NetworkResult.Success)
        assertEquals("SUCCESS_PAYLOAD", (success as NetworkResult.Success).data)

        val error: NetworkResult<String> = NetworkResult.Error(500, "Internal Server Error")
        assertTrue(error is NetworkResult.Error)
        assertEquals(500, (error as NetworkResult.Error).code)
    }
}
