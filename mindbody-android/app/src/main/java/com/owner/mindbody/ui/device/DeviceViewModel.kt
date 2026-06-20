package com.owner.mindbody.ui.device

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.sync.DeviceSyncStatus
import com.owner.mindbody.data.sync.SyncApiClient
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import com.owner.mindbody.worker.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val UNLOCK_TAP_COUNT = 7
    }

    private val app = application as MindBodyApplication
    private val polar = app.polarBleManager

    private var versionTapCount = 0

    val connectionState: StateFlow<ConnectionState> = polar.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val connectedDeviceId: StateFlow<String?> = polar.connectedDeviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scannedDevices: StateFlow<List<ScannedDevice>> = polar.scannedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batteryLevel: StateFlow<Int?> = polar.batteryLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ftuDone: StateFlow<Boolean> = polar.ftuDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val statusMessage: StateFlow<String?> = polar.statusMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedDeviceId: StateFlow<String?> = app.devicePreferences.savedDeviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionMode: StateFlow<ConnectionMode> = polar.connectionMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionMode.PERSISTENT)

    val developerModeEnabled: StateFlow<Boolean> = app.developerPreferences.developerModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 设备离线同步状态（DeviceSyncManager 自动触发，此处仅暴露给 UI）
    val deviceSyncStatus: StateFlow<DeviceSyncStatus> = app.storage.deviceSync.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceSyncStatus.IDLE)

    val deviceSyncError: StateFlow<String?> = app.storage.deviceSync.lastSyncError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 云端同步状态
    val syncBaseUrl: StateFlow<String> = app.storage.syncPreferences.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val syncApiKey: StateFlow<String> = app.storage.syncPreferences.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val syncEnabled: StateFlow<Boolean> = app.storage.syncPreferences.syncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lastSyncTime: StateFlow<Long> = app.storage.syncPreferences.lastSyncTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val lastSyncResult: StateFlow<String> = app.storage.syncPreferences.lastSyncResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val sdkVersion: String = polar.sdkVersion()

    fun scan() = polar.searchForDevices()

    fun connect(deviceId: String) = polar.connectToDevice(deviceId)

    fun disconnect() = polar.disconnect()

    fun connectSaved() {
        viewModelScope.launch {
            val id = savedDeviceId.value
            if (!id.isNullOrBlank()) {
                polar.connectSavedDevice(id)
            }
        }
    }

    fun refreshFtuStatus() {
        viewModelScope.launch {
            val id = connectedDeviceId.value ?: return@launch
            polar.checkFtuStatus(id)
        }
    }

    fun setConnectionMode(mode: ConnectionMode) = polar.setConnectionMode(mode)

    fun setSyncBaseUrl(url: String) {
        viewModelScope.launch { app.storage.syncPreferences.setBaseUrl(url) }
    }

    fun setSyncApiKey(key: String) {
        viewModelScope.launch { app.storage.syncPreferences.setApiKey(key) }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { app.storage.syncPreferences.setSyncEnabled(enabled) }
    }

    fun triggerSyncNow() {
        SyncWorker.enqueueOnce(app)
    }

    /** 连点版本信息区域，满 7 次切换开发者模式。 */
    fun onVersionAreaTap() {
        versionTapCount++
        if (versionTapCount < UNLOCK_TAP_COUNT) return
        versionTapCount = 0
        viewModelScope.launch {
            val enable = !developerModeEnabled.value
            app.developerPreferences.setDeveloperModeEnabled(enable)
            val message = if (enable) "开发者模式已开启" else "开发者模式已关闭"
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }
}
