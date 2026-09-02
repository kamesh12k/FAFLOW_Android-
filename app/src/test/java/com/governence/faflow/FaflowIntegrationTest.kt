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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaflowIntegrationTest {

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
    fun testPreferencesMapping() {
        val prefDto = SubstitutionPreferenceOutDto(
            teacherId = 42,
            maxSubstitutionsPerDay = 2,
            maxSubstitutionsPerWeek = 6,
            willingForCrossDepartment = true
        )

        assertEquals(2, prefDto.maxSubstitutionsPerDay)
        assertEquals(6, prefDto.maxSubstitutionsPerWeek)
        assertTrue(prefDto.willingForCrossDepartment)
    }

    @Test
    fun testNotificationMapping() {
        val notifDto = NotificationOutDto(
            id = 88,
            title = "Duty Assigned",
            body = "You have been assigned to cover Period 3 today.",
            eventType = "substitution_assigned",
            isRead = false,
            createdAt = "2026-09-02 08:00:00"
        )

        assertEquals(88, notifDto.id)
        assertFalse(notifDto.isRead)
        assertEquals("Duty Assigned", notifDto.title)
    }

    @Test
    fun testTeacherTodaySummaryAndResolve() {
        val summaryDto = TeacherTodaySummaryDto(
            date = "2026-09-02",
            dayOrder = 3,
            dayType = "regular",
            blocksOperations = false,
            isOnLeave = false
        )

        assertEquals(3, summaryDto.dayOrder)
        assertEquals("regular", summaryDto.dayType)
        assertFalse(summaryDto.blocksOperations)

        val resolveDto = DayOrderResolveDto(
            date = "2026-09-03",
            dayOrder = 4,
            dayType = "regular",
            blocksOperations = false
        )

        assertEquals(4, resolveDto.dayOrder)
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
