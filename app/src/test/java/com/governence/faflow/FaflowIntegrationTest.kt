package com.governence.faflow

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
import com.governence.faflow.location.GeofenceValidationResult
import com.governence.faflow.location.GeofenceValidator
import com.governence.faflow.location.StaffLiveLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaflowIntegrationTest {

    // ---------- Milestone 4: Geofence & Location Verification Tests ----------

    @Test
    fun testHaversineDistanceCalculation() {
        // Distance between two known points in Coimbatore (approx 135m)
        val lat1 = 11.016844
        val lon1 = 76.955833
        val lat2 = 11.018000
        val lon2 = 76.956000

        val distance = GeofenceMathEngine.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 130m", distance in 120.0..140.0)
    }

    @Test
    fun testCircularGeofenceInsideAndOutside() {
        val center = GeoPoint(11.016844, 76.955833)
        val radius = 150.0

        // Point 50m away from center
        val insidePoint = GeoPoint(11.017200, 76.955833)
        val (isInside, distanceInside) = GeofenceMathEngine.isInsideCircle(insidePoint, center, radius)
        assertTrue("Point should be inside circle", isInside)
        assertTrue("Distance should be < 150m", distanceInside < radius)

        // Point 500m away from center
        val outsidePoint = GeoPoint(11.021000, 76.955833)
        val (isOutside, distanceOutside) = GeofenceMathEngine.isInsideCircle(outsidePoint, center, radius)
        assertFalse("Point should be outside circle", isOutside)
        assertTrue("Distance should be > 150m", distanceOutside > radius)
    }

    @Test
    fun testPolygonalGeofenceRayCasting() {
        // Quadrilateral bounding box around Tech Park
        val vertices = listOf(
            GeoPoint(11.017000, 76.956000),
            GeoPoint(11.019000, 76.956000),
            GeoPoint(11.019000, 76.958000),
            GeoPoint(11.017000, 76.958000)
        )

        // Point in the center of the bounding box
        val pointInside = GeoPoint(11.018000, 76.957000)
        assertTrue("Center point should be inside polygon", GeofenceMathEngine.isInsidePolygon(pointInside, vertices))

        // Point clearly outside
        val pointOutside = GeoPoint(11.025000, 76.960000)
        assertFalse("Point should be outside polygon", GeofenceMathEngine.isInsidePolygon(pointOutside, vertices))
    }

    @Test
    fun testGeofenceValidatorRejectsMockLocation() {
        val validator = GeofenceValidator(maxAccuracyThresholdMeters = 30.0f)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val spoofedLocation = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracy = 5.0f,
            altitude = 100.0,
            speed = 0.0f,
            isMock = true,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validateLocation(spoofedLocation, geofences)
        assertTrue("Validator must detect and block mock location", result is GeofenceValidationResult.MockLocationDetected)
    }

    @Test
    fun testGeofenceValidatorRejectsPoorAccuracy() {
        val validator = GeofenceValidator(maxAccuracyThresholdMeters = 30.0f)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val inaccurateLocation = StaffLiveLocation(
            latitude = 11.016844,
            longitude = 76.955833,
            accuracy = 85.0f, // > 30m threshold
            altitude = 100.0,
            speed = 0.0f,
            isMock = false,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validateLocation(inaccurateLocation, geofences)
        assertTrue("Validator must flag poor accuracy", result is GeofenceValidationResult.PoorAccuracy)
        assertEquals(85.0f, (result as GeofenceValidationResult.PoorAccuracy).currentAccuracyMeters)
    }

    @Test
    fun testGeofenceValidatorApprovesValidInsideLocation() {
        val validator = GeofenceValidator(maxAccuracyThresholdMeters = 30.0f)
        val geofences = listOf(
            CampusGeofence("GEO-1", "Main Campus Block", GeofenceType.CIRCLE, 11.016844, 76.955833, 200.0)
        )

        val validLocation = StaffLiveLocation(
            latitude = 11.016900,
            longitude = 76.955850,
            accuracy = 4.5f,
            altitude = 120.0,
            speed = 0.0f,
            isMock = false,
            timestamp = System.currentTimeMillis()
        )

        val result = validator.validateLocation(validLocation, geofences)
        assertTrue("Validator must approve valid location inside campus", result is GeofenceValidationResult.Inside)
        val inside = result as GeofenceValidationResult.Inside
        assertEquals("Main Campus Block", inside.geofence.name)
        assertTrue("Distance should be < 200m", inside.distanceToCenterMeters < 200.0)
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
            TimetableSlotOutDto(id = 2, teacherId = 42, subjectId = 11, classId = 4, roomId = 8, dayOrder = 3, periodNumber = 3, subjectName = "Operating Systems", subjectCode = "CS402", className = "II B.Sc CS", classSection = "B", roomNumber = "Room 204"),
            TimetableSlotOutDto(id = 3, teacherId = 42, subjectId = 12, classId = 5, roomId = 12, dayOrder = 1, periodNumber = 2, subjectName = "Deep Learning", subjectCode = "CS801", className = "III B.Sc CS", classSection = "A", roomNumber = "Room 302")
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
        assertEquals("Operating Systems", day3Slots[1].subjectName)
        assertEquals(3, day3Slots[1].periodNumber)
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
        assertEquals(101, leave.id)
    }

    @Test
    fun testCreditsBalanceAndLedgerMapping() {
        val balanceDto = CreditBalanceOutDto(teacherId = 42, balance = 6)
        assertEquals(6, balanceDto.balance)

        val txDto = CreditTransactionOutDto(
            id = 501,
            teacherId = 42,
            change = 1,
            reason = "Substituted Period 4 for Prof. Raman",
            category = "substitute_class",
            createdAt = "2026-09-01 10:30:00"
        )

        val tx = CreditTransaction(
            id = txDto.id,
            teacherId = txDto.teacherId,
            change = txDto.change,
            category = txDto.category ?: "other",
            reason = txDto.reason,
            createdAt = txDto.createdAt
        )

        assertEquals(1, tx.change)
        assertEquals("substitute_class", tx.category)
        assertEquals(501, tx.id)
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
        assertEquals("Internal Server Error", error.message)
    }
}
