package com.governence.faflow

import com.governence.faflow.core.network.NetworkResult
import com.governence.faflow.domain.model.LeaveRequest
import com.governence.faflow.domain.model.LeaveStatus
import com.governence.faflow.domain.model.StaffMember
import com.governence.faflow.domain.model.TimetableSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaflowIntegrationTest {

    @Test
    fun testStaffMemberModel() {
        val staff = StaffMember(
            id = 42,
            name = "Dr. Kamesh V",
            email = "kamesh@institution.edu",
            username = "kamesh",
            role = "teacher",
            departmentId = 1,
            departmentName = "Computer Science",
            isActive = true
        )
        assertEquals(42, staff.id)
        assertEquals("teacher", staff.role)
        assertEquals(1, staff.departmentId)
        assertTrue(staff.isActive)
    }

    @Test
    fun testTimetableSlotMappingAndDayOrderFiltering() {
        val slots = listOf(
            TimetableSlot(1, 42, "Deep Learning", "CS801", "III B.Sc CS", "A", "Room 302", dayOrder = 3, periodNumber = 1),
            TimetableSlot(2, 42, "Operating Systems", "CS402", "II B.Sc CS", "B", "Room 204", dayOrder = 3, periodNumber = 3),
            TimetableSlot(3, 42, "Cloud Computing", "CS601", "III B.Sc CS", "A", "Room 101", dayOrder = 2, periodNumber = 2)
        )

        val day3Slots = slots.filter { it.dayOrder == 3 }.sortedBy { it.periodNumber }
        assertEquals(2, day3Slots.size)
        assertEquals("Deep Learning", day3Slots[0].subjectName)
        assertEquals("Operating Systems", day3Slots[1].subjectName)
    }

    @Test
    fun testLeaveRequestStatusMapping() {
        val leave = LeaveRequest(
            id = 10,
            teacherId = 42,
            date = "2026-09-03",
            dayOrder = 4,
            periodNumber = 2,
            reason = "Academic Conference Presentation",
            status = LeaveStatus.APPROVED,
            isEmergency = false,
            substituteTeacherName = "Prof. Priya Raman"
        )
        assertEquals(LeaveStatus.APPROVED, leave.status)
        assertEquals("Prof. Priya Raman", leave.substituteTeacherName)
        assertEquals(4, leave.dayOrder)
    }

    @Test
    fun testNetworkResultSuccessAndErrorStates() {
        val success: NetworkResult<String> = NetworkResult.Success("FAFLOW_JWT_TOKEN")
        assertTrue(success is NetworkResult.Success)
        assertEquals("FAFLOW_JWT_TOKEN", (success as NetworkResult.Success).data)

        val error: NetworkResult<String> = NetworkResult.Error(401, "Incorrect username/email or password")
        assertTrue(error is NetworkResult.Error)
        assertEquals(401, (error as NetworkResult.Error).code)
        assertEquals("Incorrect username/email or password", error.message)
    }
}
