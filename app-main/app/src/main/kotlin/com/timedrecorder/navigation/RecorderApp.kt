package com.timedrecorder.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.timedrecorder.feature.files.FilesRoute
import com.timedrecorder.feature.home.HomeRoute
import com.timedrecorder.feature.messages.MessagesRoute
import com.timedrecorder.feature.notedetail.NoteDetailRoute
import com.timedrecorder.feature.recording.ActiveRecordingRoute
import com.timedrecorder.feature.recording.RecordingScenarioRoute
import com.timedrecorder.feature.results.ResultsRoute
import com.timedrecorder.feature.schedule.ScheduleRoute
import com.timedrecorder.feature.schedule.TaskEditRoute
import com.timedrecorder.feature.settings.AboutRoute
import com.timedrecorder.feature.settings.DiagnosticRoute
import com.timedrecorder.feature.settings.SettingsRoute

/**
 * App 根 Compose：首页悬浮胶囊导航 + 场景化录音子路由。
 * 标准底部 Tab 已移除，主导航由首页胶囊承担。
 */
@Composable
fun RecorderApp() {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeRoute(
                    onNavigateToNoteDetail = { fileId ->
                        navController.navigate("note/detail/$fileId")
                    },
                    onNavigateToResults = { navController.navigate(SecondaryDestination.RESULTS) },
                    onNavigateToActiveRecording = {
                        navController.navigate(SecondaryDestination.RECORDING_ACTIVE)
                    },
                    onNavigateToSchedule = { navController.navigate(SecondaryDestination.SCHEDULE) },
                    onNavigateToFiles = { navController.navigate(TopLevelDestination.FILES.route) },
                    onNavigateToMessages = { navController.navigate(SecondaryDestination.MESSAGES) },
                    onNavigateToSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                    onNavigateToDiagnostic = { navController.navigate(SecondaryDestination.DIAGNOSTIC) },
                    onNavigateToAbout = { navController.navigate(SecondaryDestination.ABOUT) },
                )
            }

            composable(SecondaryDestination.RECORDING_SCENARIO) {
                RecordingScenarioRoute(
                    onNavigateBack = { navController.popBackStack() },
                    onStartRecording = {
                        navController.navigate(SecondaryDestination.RECORDING_ACTIVE) {
                            popUpTo(TopLevelDestination.HOME.route)
                        }
                    },
                    onNavigateToSchedule = { navController.navigate(SecondaryDestination.SCHEDULE) },
                )
            }

            composable(SecondaryDestination.RECORDING_ACTIVE) {
                ActiveRecordingRoute(
                    // 收起/保存/放弃/外部结束都 pop 回首页
                    onNavigateBack = {
                        navController.popBackStack(TopLevelDestination.HOME.route, false)
                    },
                )
            }

            composable(SecondaryDestination.SCHEDULE) {
                ScheduleRoute(
                    onNavigateToEdit = { id -> navController.navigate("schedule/edit/$id") },
                    onNavigateToAdd = { navController.navigate("schedule/edit/0") },
                )
            }

            composable(TopLevelDestination.FILES.route) {
                FilesRoute(
                    onNavigateToNoteDetail = { fileId ->
                        navController.navigate("note/detail/$fileId")
                    },
                )
            }

            composable(SecondaryDestination.MESSAGES) {
                MessagesRoute()
            }

            composable(TopLevelDestination.SETTINGS.route) {
                SettingsRoute(
                    onNavigateToDiagnostic = { navController.navigate(SecondaryDestination.DIAGNOSTIC) },
                    onNavigateToAbout = { navController.navigate(SecondaryDestination.ABOUT) },
                )
            }

            composable(
                route = SecondaryDestination.SCHEDULE_EDIT,
                arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId")
                TaskEditRoute(
                    taskId = if (taskId == 0L) null else taskId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(SecondaryDestination.DIAGNOSTIC) { DiagnosticRoute() }
            composable(SecondaryDestination.ABOUT) { AboutRoute() }
            composable(SecondaryDestination.RESULTS) {
                ResultsRoute(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNoteDetail = { fileId ->
                        navController.navigate("note/detail/$fileId")
                    },
                )
            }

            composable(
                route = SecondaryDestination.NOTE_DETAIL,
                arguments = listOf(navArgument("fileId") { type = NavType.LongType }),
            ) {
                NoteDetailRoute(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
