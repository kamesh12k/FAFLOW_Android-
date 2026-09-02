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
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.GeoPoint
import com.governence.faflow.location.GeofenceMathEngine
import com.governence.faflow.location.GeofenceType
import com.governence.faflow.location.GeofenceValidator
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.location.StaffLiveLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FaflowIntegrationTest {

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
            // 1. Throttle rate
            if (timestamp - lastProcessedTime < minIntervalMs) {
                droppedCount.incrementAndGet()
                return
            }

            // 2. Concurrency lock
            if (isWorkerBusy) {
                droppedCount.incrementAndGet()
                return
            }

            lastProcessedTime = timestamp
            processedCount.incrementAndGet()
        }

        // T = 0ms (Accepted)
        simulateIncomingFrame(0L, isWorkerBusy = false)
        assertEquals(1, processedCount.get())
        assertEquals(0, droppedCount.get())

        // T = 30ms (Dropped due to rate limit)
        simulateIncomingFrame(30L, isWorkerBusy = false)
        assertEquals(1, processedCount.get())
        assertEquals(1, droppedCount.get())

        // T = 120ms with worker busy (Dropped due to concurrent processing lock)
        simulateIncomingFrame(120L, isWorkerBusy = true)
        assertEquals(1, processedCount.get())
        assertEquals(2, droppedCount.get())

        // T = 250ms with worker free (Accepted)
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
