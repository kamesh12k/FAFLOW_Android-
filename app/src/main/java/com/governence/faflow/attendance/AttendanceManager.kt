package com.governence.faflow.attendance

import com.governence.faflow.domain.model.AttendanceRecord
import com.governence.faflow.domain.model.AttendanceSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for orchestrating attendance sessions and preventing duplicate marks.
 */
interface AttendanceManager {
    val activeSession: StateFlow<AttendanceSession?>
    val liveVerifiedStudents: StateFlow<List<AttendanceRecord>>

    suspend fun startSession(title: String, classId: String, subject: String, operatorName: String): Result<AttendanceSession>
    suspend fun markAttendance(personId: String, recognitionScore: Float, isLive: Boolean): Result<AttendanceRecord>
    suspend fun endSession(): Result<AttendanceSession>
    fun getSessionRecords(sessionId: String): Flow<List<AttendanceRecord>>
}
