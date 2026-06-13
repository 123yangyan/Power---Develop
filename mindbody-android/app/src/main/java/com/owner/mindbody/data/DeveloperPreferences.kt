package com.owner.mindbody.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_prefs")

/**
 * 开发者模式开关（隐藏入口解锁后持久化）。
 */
class DeveloperPreferences(private val context: Context) {

    private val developerModeKey = booleanPreferencesKey("developer_mode_enabled")

    val developerModeEnabled: Flow<Boolean> = context.developerDataStore.data.map { prefs ->
        prefs[developerModeKey] ?: false
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.developerDataStore.edit { prefs ->
            prefs[developerModeKey] = enabled
        }
    }
}
