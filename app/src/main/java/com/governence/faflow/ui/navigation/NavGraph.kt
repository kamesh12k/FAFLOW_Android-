package com.governence.faflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.governence.faflow.ui.screens.AddPersonScreen
import com.governence.faflow.ui.screens.AttendanceHistoryScreen
import com.governence.faflow.ui.screens.AttendanceResultScreen
import com.governence.faflow.ui.screens.AttendanceSessionListScreen
import com.governence.faflow.ui.screens.DashboardScreen
import com.governence.faflow.ui.screens.EnrollmentResultScreen
import com.governence.faflow.ui.screens.FaceEnrollmentScreen
import com.governence.faflow.ui.screens.LiveAttendanceScreen
import com.governence.faflow.ui.screens.LoginScreen
import com.governence.faflow.ui.screens.PersonDetailsScreen
import com.governence.faflow.ui.screens.PersonListScreen
import com.governence.faflow.ui.screens.SettingsScreen
import com.governence.faflow.ui.screens.SplashScreen
import com.governence.faflow.ui.screens.StartAttendanceScreen
import com.governence.faflow.ui.screens.SyncStatusScreen

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
                onNavigateToStartAttendance = { navController.navigate(Screen.StartAttendance.route) },
                onNavigateToPersonList = { navController.navigate(Screen.PersonList.route) },
                onNavigateToAttendanceHistory = { navController.navigate(Screen.AttendanceHistory.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToSyncStatus = { navController.navigate(Screen.SyncStatus.route) }
            )
        }

        composable(Screen.PersonList.route) {
            PersonListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddPerson = { navController.navigate(Screen.AddPerson.route) },
                onNavigateToPersonDetails = { personId ->
                    navController.navigate(Screen.PersonDetails.createRoute(personId))
                },
                onNavigateToEnrollment = { personId, personName ->
                    navController.navigate(Screen.FaceEnrollment.createRoute(personId, personName))
                }
            )
        }

        composable(Screen.AddPerson.route) {
            AddPersonScreen(
                onNavigateBack = { navController.popBackStack() },
                onPersonAdded = { personId, personName ->
                    navController.navigate(Screen.FaceEnrollment.createRoute(personId, personName)) {
                        popUpTo(Screen.PersonList.route)
                    }
                }
            )
        }

        composable(
            route = Screen.FaceEnrollment.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("personName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: ""
            val personName = backStackEntry.arguments?.getString("personName") ?: ""
            FaceEnrollmentScreen(
                personId = personId,
                personName = personName,
                onNavigateBack = { navController.popBackStack() },
                onEnrollmentComplete = { id, status ->
                    navController.navigate(Screen.EnrollmentResult.createRoute(id, status)) {
                        popUpTo(Screen.PersonList.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EnrollmentResult.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("status") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: ""
            val status = backStackEntry.arguments?.getString("status") ?: "SUCCESS"
            EnrollmentResultScreen(
                personId = personId,
                status = status,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onRetryEnrollment = { navController.popBackStack() }
            )
        }

        composable(Screen.SessionList.route) {
            AttendanceSessionListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStartAttendance = { navController.navigate(Screen.StartAttendance.route) },
                onNavigateToSessionDetails = { sessionId ->
                    navController.navigate(Screen.AttendanceResult.createRoute(sessionId))
                }
            )
        }

        composable(Screen.StartAttendance.route) {
            StartAttendanceScreen(
                onNavigateBack = { navController.popBackStack() },
                onSessionCreated = { sessionId, sessionTitle ->
                    navController.navigate(Screen.LiveAttendance.createRoute(sessionId, sessionTitle)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(
            route = Screen.LiveAttendance.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("sessionTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val sessionTitle = backStackEntry.arguments?.getString("sessionTitle") ?: ""
            LiveAttendanceScreen(
                sessionId = sessionId,
                sessionTitle = sessionTitle,
                onNavigateBack = { navController.popBackStack() },
                onEndSession = { id ->
                    navController.navigate(Screen.AttendanceResult.createRoute(id)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(
            route = Screen.AttendanceResult.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            AttendanceResultScreen(
                sessionId = sessionId,
                onNavigateToDashboard = {
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

        composable(
            route = Screen.PersonDetails.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: ""
            PersonDetailsScreen(
                personId = personId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEnrollment = { id, name ->
                    navController.navigate(Screen.FaceEnrollment.createRoute(id, name))
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
