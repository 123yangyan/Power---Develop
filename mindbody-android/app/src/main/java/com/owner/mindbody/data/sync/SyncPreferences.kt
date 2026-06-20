package com.owner.mindbody.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

class SyncPreferences(private val context: Context) {

    private val baseUrlKey = stringPreferencesKey("sync_base_url")
    private val apiKeyKey = stringPreferencesKey("sync_api_key")
    private val deviceIdKey = stringPreferencesKey("sync_device_id")
    private val enabledKey = booleanPreferencesKey("sync_enabled")
    private val lastSyncTimeKey = longPreferencesKey("sync_last_time")
    private val lastSyncResultKey = stringPreferencesKey("sync_last_result")

    val baseUrl: Flow<String> = context.syncDataStore.data.map { it[baseUrlKey] ?: "" }
    val apiKey: Flow<String> = context.syncDataStore.data.map { it[apiKeyKey] ?: "" }
    val deviceId: Flow<String> = context.syncDataStore.data.map { it[deviceIdKey] ?: "" }
    val syncEnabled: Flow<Boolean> = context.syncDataStore.data.map { it[enabledKey] ?: false }
    val lastSyncTime: Flow<Long> = context.syncDataStore.data.map { it[lastSyncTimeKey] ?: 0L }
    val lastSyncResult: Flow<String> = context.syncDataStore.data.map { it[lastSyncResultKey] ?: "" }

    suspend fun setBaseUrl(url: String) { context.syncDataStore.edit { it[baseUrlKey] = url } }
    suspend fun setApiKey(key: String) { context.syncDataStore.edit { it[apiKeyKey] = key } }
    suspend fun setSyncEnabled(enabled: Boolean) { context.syncDataStore.edit { it[enabledKey] = enabled } }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.syncDataStore.data.map { it[deviceIdKey] }.first()
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString().take(8)
        context.syncDataStore.edit { it[deviceIdKey] = newId }
        return newId
    }

    suspend fun setLastSyncResult(timeMs: Long, result: String) {
        context.syncDataStore.edit { prefs ->
            prefs[lastSyncTimeKey] = timeMs
            prefs[lastSyncResultKey] = result
        }
    }
}
