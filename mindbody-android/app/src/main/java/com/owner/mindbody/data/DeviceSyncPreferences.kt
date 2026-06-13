package com.owner.mindbody.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.deviceSyncStore: DataStore<Preferences> by preferencesDataStore(name = "device_sync_prefs")

/**
 * 记录各数据类型上次成功同步到的日期，避免重复拉取。
 */
class DeviceSyncPreferences(private val context: Context) {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun getLastSyncedDate(key: SyncDataType): LocalDate? {
        val prefKey = stringPreferencesKey("last_sync_${key.name}")
        val raw = context.deviceSyncStore.data.map { it[prefKey] }.first()
        return raw?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
    }

    suspend fun setLastSyncedDate(key: SyncDataType, date: LocalDate) {
        val prefKey = stringPreferencesKey("last_sync_${key.name}")
        context.deviceSyncStore.edit { prefs ->
            prefs[prefKey] = date.format(formatter)
        }
    }
}

enum class SyncDataType {
    ACTIVITY_DAY,
    ACTIVITY_MINUTE,
    HR_247,
    PPI_247,
    SKIN_TEMP_247,
    NIGHTLY_RECHARGE,
    SLEEP,
    TRAINING
}
