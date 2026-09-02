package com.governence.faflow.faflow

import com.governence.faflow.domain.model.CreditTransaction
import com.governence.faflow.domain.model.LeaveRequest
import com.governence.faflow.domain.model.TimetableSlot
import kotlinx.coroutines.flow.Flow

/**
 * Contracts for accessing FAFLOW academic scheduling, timetable, and day order rotations.
 */
interface TimetableRepository {
    suspend fun getTodayDayOrder(): Result<Int>
    suspend fun getTeacherTimetable(teacherId: Int): Result<List<TimetableSlot>>
    suspend fun getTodaySchedule(teacherId: Int): Result<List<TimetableSlot>>
}

/**
 * Contracts for FAFLOW faculty leave applications, emergency leaves, and history.
 */
interface LeaveRepository {
    suspend fun getMyLeaves(): Result<List<LeaveRequest>>
    suspend fun applyLeave(date: String, dayOrder: Int, periodNumber: Int, reason: String): Result<LeaveRequest>
    suspend fun cancelLeave(leaveId: Int): Result<Unit>
}

/**
 * Contracts for FAFLOW faculty workload credit ledger (+1 / -1).
 */
interface CreditRepository {
    suspend fun getCreditBalance(teacherId: Int): Result<Int>
    suspend fun getCreditHistory(): Result<List<CreditTransaction>>
}

/**
 * Contracts for FAFLOW teacher self-substitution delegation and duties.
 */
interface SubstitutionRepository {
    suspend fun getMySubstituteDuties(): Result<List<LeaveRequest>>
    suspend fun getCandidatesForLeave(leaveId: Int): Result<List<Pair<Int, String>>> // (teacherId, name)
    suspend fun assignSubstitute(leaveId: Int, substituteTeacherId: Int): Result<Unit>
}
