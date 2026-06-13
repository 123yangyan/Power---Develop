package com.owner.mindbody.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.moodDataStore: DataStore<Preferences> by preferencesDataStore(name = "mood_prefs")

/**
 * 心情提醒与静默时段设置，对齐 emotion v3.7 settings.ts + daily-checkin-service。
 */
class MoodPreferences(private val context: Context) {

    private val reminderIntervalKey = intPreferencesKey("reminder_interval_minutes")
    private val quietStartKey = stringPreferencesKey("quiet_start")
    private val quietEndKey = stringPreferencesKey("quiet_end")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val strongPopupKey = booleanPreferencesKey("strong_popup")
    private val lastReminderAtKey = longPreferencesKey("last_reminder_at")

    val reminderIntervalMinutes: Flow<Int> = context.moodDataStore.data.map { prefs ->
        clampReminderInterval(prefs[reminderIntervalKey] ?: DEFAULT_REMINDER_INTERVAL_MINUTES)
    }

    val quietStart: Flow<String> = context.moodDataStore.data.map { prefs ->
        prefs[quietStartKey] ?: DEFAULT_QUIET_START
    }

    val quietEnd: Flow<String> = context.moodDataStore.data.map { prefs ->
        prefs[quietEndKey] ?: DEFAULT_QUIET_END
    }

    val notificationsEnabled: Flow<Boolean> = context.moodDataStore.data.map { prefs ->
        prefs[notificationsEnabledKey] ?: true
    }

    val strongPopup: Flow<Boolean> = context.moodDataStore.data.map { prefs ->
        prefs[strongPopupKey] ?: true
    }

    val lastReminderAt: Flow<Long> = context.moodDataStore.data.map { prefs ->
        prefs[lastReminderAtKey] ?: 0L
    }

    suspend fun setReminderIntervalMinutes(minutes: Int) {
        context.moodDataStore.edit { prefs ->
            prefs[reminderIntervalKey] = clampReminderInterval(minutes)
        }
    }

    suspend fun setQuietHours(start: String, end: String) {
        context.moodDataStore.edit { prefs ->
            prefs[quietStartKey] = start
            prefs[quietEndKey] = end
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.moodDataStore.edit { prefs ->
            prefs[notificationsEnabledKey] = enabled
        }
    }

    suspend fun setStrongPopup(enabled: Boolean) {
        context.moodDataStore.edit { prefs ->
            prefs[strongPopupKey] = enabled
        }
    }

    suspend fun setLastReminderAt(timestamp: Long = System.currentTimeMillis()) {
        context.moodDataStore.edit { prefs ->
            prefs[lastReminderAtKey] = timestamp
        }
    }

    suspend fun getReminderIntervalMinutesSync(): Int {
        return clampReminderInterval(
            context.moodDataStore.data.first()[reminderIntervalKey] ?: DEFAULT_REMINDER_INTERVAL_MINUTES
        )
    }

    suspend fun getQuietStartSync(): String {
        return context.moodDataStore.data.first()[quietStartKey] ?: DEFAULT_QUIET_START
    }

    suspend fun getQuietEndSync(): String {
        return context.moodDataStore.data.first()[quietEndKey] ?: DEFAULT_QUIET_END
    }

    suspend fun isNotificationsEnabledSync(): Boolean {
        return context.moodDataStore.data.first()[notificationsEnabledKey] ?: true
    }

    suspend fun isStrongPopupSync(): Boolean {
        return context.moodDataStore.data.first()[strongPopupKey] ?: true
    }

    suspend fun getLastReminderAtSync(): Long {
        return context.moodDataStore.data.first()[lastReminderAtKey] ?: 0L
    }

    /** 当日 Esc snooze 次数，对齐 checkinSnoozeCount_{date} */
    suspend fun getSnoozeCount(dateKey: String): Int {
        val key = stringPreferencesKey("checkin_snooze_count_$dateKey")
        return context.moodDataStore.data.first()[key]?.toIntOrNull() ?: 0
    }

    suspend fun incrementSnoozeCount(dateKey: String) {
        val key = stringPreferencesKey("checkin_snooze_count_$dateKey")
        context.moodDataStore.edit { prefs ->
            val current = prefs[key]?.toIntOrNull() ?: 0
            prefs[key] = (current + 1).toString()
        }
    }

    /** snooze 后 20 分钟，否则用户间隔（对齐 getEffectiveIntervalMs） */
    suspend fun getEffectiveIntervalMs(): Long {
        val dateKey = todayDateKey()
        val snoozeCount = getSnoozeCount(dateKey)
        return if (snoozeCount > 0) {
            MoodCheckInConstants.SNOOZE_INTERVAL_MS
        } else {
            getReminderIntervalMinutesSync() * 60_000L
        }
    }

    fun isQuietHours(nowMinutes: Int, quietStart: String, quietEnd: String): Boolean {
        val start = parseTimeToMinutes(quietStart)
        val end = parseTimeToMinutes(quietEnd)
        return if (start <= end) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    fun currentMinutesOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }

    fun todayDateKey(): String {
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    companion object {
        const val DEFAULT_REMINDER_INTERVAL_MINUTES = 60
        const val MIN_REMINDER_INTERVAL_MINUTES = 1
        const val MAX_REMINDER_INTERVAL_MINUTES = 1440
        const val DEFAULT_QUIET_START = "22:00"
        const val DEFAULT_QUIET_END = "08:00"

        fun clampReminderInterval(minutes: Int): Int {
            return minutes.coerceIn(MIN_REMINDER_INTERVAL_MINUTES, MAX_REMINDER_INTERVAL_MINUTES)
        }

        private fun parseTimeToMinutes(time: String): Int {
            return runCatching {
                val parts = time.split(":")
                parts[0].toInt() * 60 + parts.getOrElse(1) { "0" }.toInt()
            }.getOrDefault(0)
        }
    }
}

/** 避免 MoodPreferences 依赖 ui 层，常量副本供 preferences 使用 */
private object MoodCheckInConstants {
    const val SNOOZE_INTERVAL_MS = 20 * 60 * 1000L
}
