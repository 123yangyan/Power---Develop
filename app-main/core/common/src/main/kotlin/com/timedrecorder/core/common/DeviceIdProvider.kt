package com.timedrecorder.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timedrecorder.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceIdDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "device_id_store",
)

private val KEY_DEVICE_ID = stringPreferencesKey("device_id")

/**
 * 设备 ID 提供者：首次启动生成 UUID 并持久化。
 * 对应 PRD §14.0 deviceId 约定。
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** 监听 deviceId 变化 */
    val deviceIdFlow: Flow<String> = context.deviceIdDataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID].orEmpty()
    }

    /** 获取完整 deviceId，若不存在则生成并持久化 */
    suspend fun getDeviceId(): String = withContext(ioDispatcher) {
        val existing = context.deviceIdDataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return@withContext existing

        val newId = UUID.randomUUID().toString()
        context.deviceIdDataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = newId
        }
        newId
    }

    /** 获取 deviceId 前 8 位，用于文件命名 */
    suspend fun getDeviceIdShort(): String {
        val id = getDeviceId()
        return id.take(AppConstants.DEVICE_ID_SHORT_LENGTH)
    }
}
