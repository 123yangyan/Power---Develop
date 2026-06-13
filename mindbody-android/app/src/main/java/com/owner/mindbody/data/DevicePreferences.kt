package com.owner.mindbody.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.owner.mindbody.polar.ConnectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

/**
 * 持久化已配对设备 ID 与 FTU 完成状态。
 */
class DevicePreferences(private val context: Context) {

    private val deviceIdKey = stringPreferencesKey("polar_device_id")
    private val ftuDoneKey = booleanPreferencesKey("ftu_done")
    private val connectionModeKey = stringPreferencesKey("connection_mode")

    val savedDeviceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[deviceIdKey]
    }

    val ftuDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ftuDoneKey] ?: false
    }

    val connectionMode: Flow<ConnectionMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[connectionModeKey] ?: ConnectionMode.PERSISTENT.name
        runCatching { ConnectionMode.valueOf(raw) }.getOrDefault(ConnectionMode.PERSISTENT)
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[deviceIdKey] = deviceId
        }
    }

    suspend fun setFtuDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ftuDoneKey] = done
        }
    }

    suspend fun setConnectionMode(mode: ConnectionMode) {
        context.dataStore.edit { prefs ->
            prefs[connectionModeKey] = mode.name
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
