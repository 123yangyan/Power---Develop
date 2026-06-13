package com.owner.mindbody.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.owner.mindbody.MainActivity
import com.owner.mindbody.ui.components.DefaultNavTabs
import com.owner.mindbody.ui.components.FloatingIslandNav
import com.owner.mindbody.ui.device.DeviceScreen
import com.owner.mindbody.ui.ftu.FtuScreen
import com.owner.mindbody.ui.heartrate.HeartRateScreen
import com.owner.mindbody.ui.mood.MoodHistoryScreen
import com.owner.mindbody.ui.mood.MoodRecordScreen
import com.owner.mindbody.ui.sensors.SensorsScreen
import com.owner.mindbody.ui.theme.MindBodyColors

sealed class AppRoute(val route: String, val label: String) {
    data object HeartRate : AppRoute("heart_rate", "心率")
    data object MoodRecord : AppRoute("mood_record", "记录")
    data object MoodHistory : AppRoute("mood_history", "历史")
    data object Sensors : AppRoute("sensors", "传感器")
    data object Device : AppRoute("device", "设备")
    data object Ftu : AppRoute("ftu/{deviceId}", "FTU") {
        fun create(deviceId: String) = "ftu/$deviceId"
    }
}

@Composable
fun AppNavigation(initialRoute: String? = null) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val bottomRoutes = listOf(
        AppRoute.HeartRate,
        AppRoute.MoodRecord,
        AppRoute.MoodHistory,
        AppRoute.Sensors,
        AppRoute.Device
    )
    val showBottomBar = currentRoute in bottomRoutes.map { it.route }

    LaunchedEffect(initialRoute) {
        if (initialRoute != null && initialRoute in bottomRoutes.map { it.route }) {
            navController.navigate(initialRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
            .statusBarsPadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.HeartRate.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) 88.dp else 0.dp)
        ) {
            composable(AppRoute.HeartRate.route) {
                HeartRateScreen()
            }
            composable(AppRoute.MoodRecord.route) {
                MoodRecordScreen()
            }
            composable(AppRoute.MoodHistory.route) {
                MoodHistoryScreen()
            }
            composable(AppRoute.Sensors.route) {
                SensorsScreen()
            }
            composable(AppRoute.Device.route) {
                DeviceScreen(
                    onNavigateToFtu = { deviceId ->
                        navController.navigate(AppRoute.Ftu.create(deviceId))
                    }
                )
            }
            composable(AppRoute.Ftu.route) { entry ->
                val deviceId = entry.arguments?.getString("deviceId") ?: return@composable
                FtuScreen(
                    deviceId = deviceId,
                    onDone = { navController.popBackStack() }
                )
            }
        }

        if (showBottomBar) {
            FloatingIslandNav(
                tabs = DefaultNavTabs,
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
