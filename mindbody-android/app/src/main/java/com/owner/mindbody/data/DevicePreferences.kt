package com.owner.mindbody.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    companion object {
        const val DEFAULT_BEDTIME_HOUR = 23
        const val DEFAULT_WAKE_HOUR = 7
        const val DEFAULT_BLE_NIGHTLY_SCHEDULE_ENABLED = true
    }

    private val deviceIdKey = stringPreferencesKey("polar_device_id")
    private val ftuDoneKey = booleanPreferencesKey("ftu_done")
    private val connectionModeKey = stringPreferencesKey("connection_mode")
    private val batteryInputMinKey = intPreferencesKey("battery_input_min")
    private val batteryInputMaxKey = intPreferencesKey("battery_input_max")
    private val bedtimeHourKey = intPreferencesKey("ble_bedtime_hour")
    private val wakeHourKey = intPreferencesKey("ble_wake_hour")
    private val bleNightlyScheduleEnabledKey = booleanPreferencesKey("ble_nightly_schedule_enabled")
    /** CDM 返回的 association id；0 表示未关联，-1 表示已关联但 API 26–32 无 id */
    private val companionAssociationIdKey = intPreferencesKey("companion_association_id")
    private val companionDeviceMacKey = stringPreferencesKey("companion_device_mac")

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

    val batteryInputMin: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[batteryInputMinKey] ?: 0
    }

    val batteryInputMax: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[batteryInputMaxKey] ?: 100
    }

    val bedtimeHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[bedtimeHourKey] ?: DEFAULT_BEDTIME_HOUR
    }

    val wakeHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[wakeHourKey] ?: DEFAULT_WAKE_HOUR
    }

    val bleNightlyScheduleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[bleNightlyScheduleEnabledKey] ?: DEFAULT_BLE_NIGHTLY_SCHEDULE_ENABLED
    }

    val companionAssociationId: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[companionAssociationIdKey] ?: 0
    }

    val companionDeviceMac: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[companionDeviceMacKey]
    }

    suspend fun setBatteryInputMin(min: Int) {
        context.dataStore.edit { prefs ->
            prefs[batteryInputMinKey] = min
        }
    }

    suspend fun setBatteryInputMax(max: Int) {
        context.dataStore.edit { prefs ->
            prefs[batteryInputMaxKey] = max
        }
    }

    suspend fun setBedtimeHour(hour: Int) {
        context.dataStore.edit { prefs ->
            prefs[bedtimeHourKey] = hour.coerceIn(0, 23)
        }
    }

    suspend fun setWakeHour(hour: Int) {
        context.dataStore.edit { prefs ->
            prefs[wakeHourKey] = hour.coerceIn(0, 23)
        }
    }

    suspend fun setBleNightlyScheduleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[bleNightlyScheduleEnabledKey] = enabled
        }
    }

    suspend fun setCompanionAssociation(associationId: Int, deviceMac: String?) {
        context.dataStore.edit { prefs ->
            prefs[companionAssociationIdKey] = associationId
            if (deviceMac.isNullOrBlank()) {
                prefs.remove(companionDeviceMacKey)
            } else {
                prefs[companionDeviceMacKey] = deviceMac
            }
        }
    }

    suspend fun clearCompanionAssociation() {
        context.dataStore.edit { prefs ->
            prefs.remove(companionAssociationIdKey)
            prefs.remove(companionDeviceMacKey)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
