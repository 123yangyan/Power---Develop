package com.timedrecorder.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目的地 — 精简为 3 项：首页 / 文件 / 设置。
 * 任务、消息等收入首页「更多」抽屉。
 */
enum class TopLevelDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Default.Home),
    FILES("files", "文件", Icons.Default.Storage),
    SETTINGS("settings", "设置", Icons.Default.Settings),
}

/** 二级路由（无底部 Tab） */
object SecondaryDestination {
    const val SCHEDULE = "schedule"
    const val MESSAGES = "messages"
    const val RESULTS = "results"
    const val RECORDING_SCENARIO = "recording/scenario"
    const val RECORDING_ACTIVE = "recording/active"
    const val SCHEDULE_EDIT = "schedule/edit/{taskId}"
    const val DIAGNOSTIC = "settings/diagnostic"
    const val ABOUT = "settings/about"
    const val NOTE_DETAIL = "note/detail/{fileId}"
}
