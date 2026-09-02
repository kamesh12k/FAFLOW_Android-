package com.governence.faflow.ui.navigation

/**
 * Type-safe navigation routes for all 16 application screens.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object PersonList : Screen("person_list")
    data object AddPerson : Screen("add_person")
    data object FaceEnrollment : Screen("face_enrollment/{personId}/{personName}") {
        fun createRoute(personId: String, personName: String) = "face_enrollment/$personId/$personName"
    }
    data object EnrollmentResult : Screen("enrollment_result/{personId}/{status}") {
        fun createRoute(personId: String, status: String) = "enrollment_result/$personId/$status"
    }
    data object SessionList : Screen("session_list")
    data object StartAttendance : Screen("start_attendance")
    data object LiveAttendance : Screen("live_attendance/{sessionId}/{sessionTitle}") {
        fun createRoute(sessionId: String, sessionTitle: String) = "live_attendance/$sessionId/$sessionTitle"
    }
    data object AttendanceResult : Screen("attendance_result/{sessionId}") {
        fun createRoute(sessionId: String) = "attendance_result/$sessionId"
    }
    data object AttendanceHistory : Screen("attendance_history")
    data object PersonDetails : Screen("person_details/{personId}") {
        fun createRoute(personId: String) = "person_details/$personId"
    }
    data object Settings : Screen("settings")
    data object SyncStatus : Screen("sync_status")
}
