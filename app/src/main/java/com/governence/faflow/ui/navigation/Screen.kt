package com.governence.faflow.ui.navigation

/**
 * Type-safe navigation routes for FAFLOW Staff Mobile application.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    
    // Palgeo Staff Attendance Screens
    data object AttendanceCheckInOut : Screen("attendance_check_in_out")
    data object AttendanceHistory : Screen("attendance_history")
    data object FaceEnrollment : Screen("face_enrollment")
    
    // FAFLOW Core Staff Modules
    data object Timetable : Screen("timetable")
    data object ApplyLeave : Screen("apply_leave")
    data object LeaveHistory : Screen("leave_history")
    data object Credits : Screen("credits")
    data object Substitution : Screen("substitution")
    data object Preferences : Screen("preferences")
    data object Notifications : Screen("notifications")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
    data object SyncStatus : Screen("sync_status")
}
