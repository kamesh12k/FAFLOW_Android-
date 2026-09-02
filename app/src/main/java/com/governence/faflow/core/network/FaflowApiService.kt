package com.governence.faflow.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface connecting directly to FAFLOW FastAPI backend endpoints.
 */
interface FaflowApiService {

    // ---------- Auth ----------
    @POST("auth/login")
    suspend fun login(
        @Body request: UserLoginRequestDto
    ): Response<TokenDto>

    @GET("teachers/me")
    suspend fun getMe(): Response<UserOutDto>

    // ---------- Academic Calendar & Day Order ----------
    @GET("academic-calendar/my-today-summary")
    suspend fun getMyTodaySummary(
        @Query("date") date: String? = null
    ): Response<TeacherTodaySummaryDto>

    @GET("academic-calendar/resolve")
    suspend fun resolveDayOrder(
        @Query("date") date: String
    ): Response<DayOrderResolveDto>

    // ---------- Timetable ----------
    @GET("timetable/teacher/{teacher_id}")
    suspend fun getTimetableByTeacher(
        @Path("teacher_id") teacherId: Int
    ): Response<List<TimetableSlotOutDto>>

    // ---------- Leaves ----------
    @GET("leaves/my")
    suspend fun getMyLeaves(
        @Query("include_expired") includeExpired: Boolean = false
    ): Response<List<LeaveOutDto>>

    @POST("leaves/")
    suspend fun applyLeave(
        @Body request: LeaveCreateDto
    ): Response<LeaveOutDto>

    @POST("leaves/batch")
    suspend fun applyLeaveBatch(
        @Body request: LeaveBatchCreateDto
    ): Response<List<LeaveOutDto>>

    @DELETE("leaves/{leave_id}")
    suspend fun cancelLeave(
        @Path("leave_id") leaveId: Int
    ): Response<Unit>

    // ---------- Credits ----------
    @GET("teachers/{teacher_id}/credits")
    suspend fun getTeacherCredits(
        @Path("teacher_id") teacherId: Int
    ): Response<CreditBalanceOutDto>

    @GET("credits/my/transactions")
    suspend fun getMyCreditTransactions(): Response<List<CreditTransactionOutDto>>

    // ---------- Teacher Substitution ----------
    @GET("teacher/substitution/my-leaves")
    suspend fun getTeacherSubstitutionDuties(
        @Query("include_expired") includeExpired: Boolean = false
    ): Response<List<LeaveOutDto>>

    @GET("teacher/substitution/leave/{leave_id}/candidates")
    suspend fun getSubstitutionCandidates(
        @Path("leave_id") leaveId: Int,
        @Query("include_cross_department") includeCrossDept: Boolean = false
    ): Response<List<RecommendationOutDto>>

    @POST("teacher/substitution/leave/{leave_id}/assign/{substitute_id}")
    suspend fun assignSubstitute(
        @Path("leave_id") leaveId: Int,
        @Path("substitute_id") substituteId: Int
    ): Response<AlterAssignmentOutDto>

    @POST("teacher/substitution/leave/{leave_id}/undo-assignment")
    suspend fun undoSubstitutionAssignment(
        @Path("leave_id") leaveId: Int
    ): Response<LeaveOutDto>

    // ---------- Preferences ----------
    @GET("campus-operations/preferences/me")
    suspend fun getMyPreferences(): Response<SubstitutionPreferenceOutDto>

    @PUT("campus-operations/preferences/me")
    suspend fun updateMyPreferences(
        @Body request: SubstitutionPreferenceUpdateDto
    ): Response<SubstitutionPreferenceOutDto>

    // ---------- Notifications ----------
    @GET("notifications/")
    suspend fun listNotifications(
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<List<NotificationOutDto>>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountDto>

    @PATCH("notifications/{notification_id}/read")
    suspend fun markNotificationRead(
        @Path("notification_id") notificationId: Int
    ): Response<StatusOkDto>

    @PATCH("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<StatusOkDto>
}
