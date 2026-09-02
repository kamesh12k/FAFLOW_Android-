package com.governence.faflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.governence.faflow.ui.screens.ApplyLeaveScreen
import com.governence.faflow.ui.screens.AttendanceCheckInOutScreen
import com.governence.faflow.ui.screens.AttendanceHistoryScreen
import com.governence.faflow.ui.screens.CreditsScreen
import com.governence.faflow.ui.screens.DashboardScreen
import com.governence.faflow.ui.screens.FaceEnrollmentScreen
import com.governence.faflow.ui.screens.LeaveHistoryScreen
import com.governence.faflow.ui.screens.LoginScreen
import com.governence.faflow.ui.screens.NotificationsScreen
import com.governence.faflow.ui.screens.PreferencesScreen
import com.governence.faflow.ui.screens.ProfileScreen
import com.governence.faflow.ui.screens.SettingsScreen
import com.governence.faflow.ui.screens.SplashScreen
import com.governence.faflow.ui.screens.SubstitutionScreen
import com.governence.faflow.ui.screens.SyncStatusScreen
import com.governence.faflow.ui.screens.TimetableScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToCheckIn = { navController.navigate(Screen.AttendanceCheckInOut.route) },
                onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                onNavigateToApplyLeave = { navController.navigate(Screen.ApplyLeave.route) },
                onNavigateToLeaveHistory = { navController.navigate(Screen.LeaveHistory.route) },
                onNavigateToCredits = { navController.navigate(Screen.Credits.route) },
                onNavigateToSubstitution = { navController.navigate(Screen.Substitution.route) },
                onNavigateToAttendanceHistory = { navController.navigate(Screen.AttendanceHistory.route) },
                onNavigateToFaceEnrollment = { navController.navigate(Screen.FaceEnrollment.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToSyncStatus = { navController.navigate(Screen.SyncStatus.route) }
            )
        }

        // Palgeo Staff Attendance Screens
        composable(Screen.AttendanceCheckInOut.route) {
            AttendanceCheckInOutScreen(
                onNavigateBack = { navController.popBackStack() },
                onAttendanceSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AttendanceHistory.route) {
            AttendanceHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.FaceEnrollment.route) {
            FaceEnrollmentScreen(
                onNavigateBack = { navController.popBackStack() },
                onEnrollmentComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // FAFLOW Core Staff Modules
        composable(Screen.Timetable.route) {
            TimetableScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ApplyLeave.route) {
            ApplyLeaveScreen(
                onNavigateBack = { navController.popBackStack() },
                onLeaveSubmitted = {
                    navController.navigate(Screen.LeaveHistory.route) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.LeaveHistory.route) {
            LeaveHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Credits.route) {
            CreditsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Substitution.route) {
            SubstitutionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Preferences.route) {
            PreferencesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFaceEnrollment = { navController.navigate(Screen.FaceEnrollment.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SyncStatus.route) {
            SyncStatusScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
