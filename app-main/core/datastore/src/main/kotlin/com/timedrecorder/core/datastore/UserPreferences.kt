package com.timedrecorder.core.datastore

import com.timedrecorder.core.common.AppConstants
import com.timedrecorder.core.model.AudioBitrate
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.HomeCardId
import com.timedrecorder.core.model.ThemeMode

/**
 * 用户偏好设置模型，对应 PRD §9.10 与 §14.0。
 * V1.1 新增：themeMode、audioBitrate、quietMode 相关、homeCardOrder。
 */
data class UserPreferences(
    val baseUrl: String = "",
    val apiKey: String = "",
    val pollIntervalSeconds: Int = AppConstants.DEFAULT_POLL_INTERVAL_SECONDS,
    val pollMaxAttempts: Int = AppConstants.DEFAULT_POLL_MAX_ATTEMPTS,
    val sliceDurationMinutes: Int = AppConstants.DEFAULT_SLICE_DURATION_MINUTES,
    val retentionDays: Int = AppConstants.DEFAULT_RETENTION_DAYS,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val wifiOnlyUpload: Boolean = false,
    val notificationEnabled: Boolean = true,
    val alertEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    // ---- T6：外观主题 ----
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // ---- T7：录音质量档位 ----
    val audioBitrate: AudioBitrate = AudioBitrate.KBPS_64,
    // ---- T8：通知静音时段 ----
    val quietModeEnabled: Boolean = false,
    /** 静音开始时间（分钟数，例如 22×60 = 1320 代表 22:00） */
    val quietStartMinutes: Int = 22 * 60,
    /** 静音结束时间（分钟数，例如 7×60 = 420 代表 07:00） */
    val quietEndMinutes: Int = 7 * 60,
    // ---- T12：首页卡片排序 ----
    val homeCardOrder: List<HomeCardId> = HomeCardId.entries,
)
