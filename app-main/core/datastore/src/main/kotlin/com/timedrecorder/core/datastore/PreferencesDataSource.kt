package com.timedrecorder.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timedrecorder.core.common.AppConstants
import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.model.AudioBitrate
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.HomeCardId
import com.timedrecorder.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

/** DataStore 键定义（V1.1 新增键以注释标注） */
private object PrefKeys {
    val BASE_URL = stringPreferencesKey("base_url")
    val API_KEY = stringPreferencesKey("api_key")
    val POLL_INTERVAL = intPreferencesKey("poll_interval_seconds")
    val POLL_MAX_ATTEMPTS = intPreferencesKey("poll_max_attempts")
    val SLICE_DURATION = intPreferencesKey("slice_duration_minutes")
    val RETENTION_DAYS = intPreferencesKey("retention_days")
    val AUDIO_FORMAT = stringPreferencesKey("audio_format")
    val WIFI_ONLY_UPLOAD = booleanPreferencesKey("wifi_only_upload")
    val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
    val ALERT_ENABLED = booleanPreferencesKey("alert_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    // V1.1 新增
    val THEME_MODE = stringPreferencesKey("theme_mode")           // T6
    val AUDIO_BITRATE = stringPreferencesKey("audio_bitrate")     // T7
    val QUIET_MODE_ENABLED = booleanPreferencesKey("quiet_mode_enabled")  // T8
    val QUIET_START_MINUTES = intPreferencesKey("quiet_start_minutes")    // T8
    val QUIET_END_MINUTES = intPreferencesKey("quiet_end_minutes")        // T8
    val HOME_CARD_ORDER = stringPreferencesKey("home_card_order") // T12
}

/**
 * 用户偏好 DataStore 读写数据源。
 */
@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val userPreferences: Flow<UserPreferences> =
        context.userPreferencesDataStore.data.map { prefs -> prefs.toUserPreferences() }

    suspend fun updateBaseUrl(url: String) = update { it.copy(baseUrl = url.trimEnd('/')) }

    suspend fun updateApiKey(key: String) = update { it.copy(apiKey = key) }

    suspend fun updatePollSettings(intervalSeconds: Int, maxAttempts: Int) = update {
        it.copy(pollIntervalSeconds = intervalSeconds, pollMaxAttempts = maxAttempts)
    }

    suspend fun updateSliceDuration(minutes: Int) = update { it.copy(sliceDurationMinutes = minutes) }

    suspend fun updateRetentionDays(days: Int) = update { it.copy(retentionDays = days) }

    suspend fun updateAudioFormat(format: AudioFormat) = update { it.copy(audioFormat = format) }

    suspend fun updateWifiOnlyUpload(enabled: Boolean) = update { it.copy(wifiOnlyUpload = enabled) }

    suspend fun updateNotificationEnabled(enabled: Boolean) = update {
        it.copy(notificationEnabled = enabled)
    }

    suspend fun updateAlertEnabled(enabled: Boolean) = update { it.copy(alertEnabled = enabled) }

    suspend fun setOnboardingCompleted(completed: Boolean) = update {
        it.copy(onboardingCompleted = completed)
    }

    // ---- T6：主题模式 ----
    suspend fun updateThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    // ---- T7：录音质量档位 ----
    suspend fun updateAudioBitrate(bitrate: AudioBitrate) = update { it.copy(audioBitrate = bitrate) }

    // ---- T8：静音时段 ----
    suspend fun updateQuietModeEnabled(enabled: Boolean) = update { it.copy(quietModeEnabled = enabled) }

    suspend fun updateQuietStartMinutes(minutes: Int) = update { it.copy(quietStartMinutes = minutes) }

    suspend fun updateQuietEndMinutes(minutes: Int) = update { it.copy(quietEndMinutes = minutes) }

    // ---- T12：首页卡片顺序 ----
    suspend fun updateHomeCardOrder(order: List<HomeCardId>) = update { it.copy(homeCardOrder = order) }

    /** 批量更新偏好设置 */
    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        withContext(ioDispatcher) {
            context.userPreferencesDataStore.edit { prefs ->
                val current = prefs.toUserPreferences()
                val updated = transform(current)
                prefs.writeFrom(updated)
            }
        }
    }

    private suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        updatePreferences(transform)
    }

    // ---- 扩展函数：从 DataStore Preferences 解析成 UserPreferences ----
    private fun Preferences.toUserPreferences() = UserPreferences(
        baseUrl = this[PrefKeys.BASE_URL].orEmpty(),
        apiKey = this[PrefKeys.API_KEY].orEmpty(),
        pollIntervalSeconds = this[PrefKeys.POLL_INTERVAL]
            ?: AppConstants.DEFAULT_POLL_INTERVAL_SECONDS,
        pollMaxAttempts = this[PrefKeys.POLL_MAX_ATTEMPTS]
            ?: AppConstants.DEFAULT_POLL_MAX_ATTEMPTS,
        sliceDurationMinutes = this[PrefKeys.SLICE_DURATION]
            ?: AppConstants.DEFAULT_SLICE_DURATION_MINUTES,
        retentionDays = this[PrefKeys.RETENTION_DAYS]
            ?: AppConstants.DEFAULT_RETENTION_DAYS,
        audioFormat = this[PrefKeys.AUDIO_FORMAT]?.let {
            runCatching { AudioFormat.valueOf(it) }.getOrDefault(AudioFormat.M4A)
        } ?: AudioFormat.M4A,
        wifiOnlyUpload = this[PrefKeys.WIFI_ONLY_UPLOAD] ?: false,
        notificationEnabled = this[PrefKeys.NOTIFICATION_ENABLED] ?: true,
        alertEnabled = this[PrefKeys.ALERT_ENABLED] ?: true,
        onboardingCompleted = this[PrefKeys.ONBOARDING_COMPLETED] ?: false,
        themeMode = this[PrefKeys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM,
        audioBitrate = this[PrefKeys.AUDIO_BITRATE]?.let {
            runCatching { AudioBitrate.valueOf(it) }.getOrDefault(AudioBitrate.KBPS_64)
        } ?: AudioBitrate.KBPS_64,
        quietModeEnabled = this[PrefKeys.QUIET_MODE_ENABLED] ?: false,
        quietStartMinutes = this[PrefKeys.QUIET_START_MINUTES] ?: (22 * 60),
        quietEndMinutes = this[PrefKeys.QUIET_END_MINUTES] ?: (7 * 60),
        homeCardOrder = this[PrefKeys.HOME_CARD_ORDER]?.let { parseCardOrder(it) }
            ?: HomeCardId.entries,
    )

    // ---- 扩展函数：将 UserPreferences 写入 DataStore MutablePreferences ----
    private fun androidx.datastore.preferences.core.MutablePreferences.writeFrom(prefs: UserPreferences) {
        this[PrefKeys.BASE_URL] = prefs.baseUrl
        this[PrefKeys.API_KEY] = prefs.apiKey
        this[PrefKeys.POLL_INTERVAL] = prefs.pollIntervalSeconds
        this[PrefKeys.POLL_MAX_ATTEMPTS] = prefs.pollMaxAttempts
        this[PrefKeys.SLICE_DURATION] = prefs.sliceDurationMinutes
        this[PrefKeys.RETENTION_DAYS] = prefs.retentionDays
        this[PrefKeys.AUDIO_FORMAT] = prefs.audioFormat.name
        this[PrefKeys.WIFI_ONLY_UPLOAD] = prefs.wifiOnlyUpload
        this[PrefKeys.NOTIFICATION_ENABLED] = prefs.notificationEnabled
        this[PrefKeys.ALERT_ENABLED] = prefs.alertEnabled
        this[PrefKeys.ONBOARDING_COMPLETED] = prefs.onboardingCompleted
        this[PrefKeys.THEME_MODE] = prefs.themeMode.name
        this[PrefKeys.AUDIO_BITRATE] = prefs.audioBitrate.name
        this[PrefKeys.QUIET_MODE_ENABLED] = prefs.quietModeEnabled
        this[PrefKeys.QUIET_START_MINUTES] = prefs.quietStartMinutes
        this[PrefKeys.QUIET_END_MINUTES] = prefs.quietEndMinutes
        this[PrefKeys.HOME_CARD_ORDER] = encodeCardOrder(prefs.homeCardOrder)
    }

    /** 将卡片顺序列表序列化为逗号分隔字符串 */
    private fun encodeCardOrder(order: List<HomeCardId>): String =
        order.joinToString(",") { it.name }

    /** 从逗号分隔字符串解析卡片顺序列表 */
    private fun parseCardOrder(raw: String): List<HomeCardId> {
        val known = raw.split(",").mapNotNull { name ->
            runCatching { HomeCardId.valueOf(name.trim()) }.getOrNull()
        }.toMutableList()
        // 补全可能的新卡片（保证向后兼容）
        HomeCardId.entries.forEach { card ->
            if (card !in known) known.add(card)
        }
        return known
    }
}
