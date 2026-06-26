package com.owner.mindbody.ui.device

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.IntentSender
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.sync.DeviceSyncStatus
import com.owner.mindbody.keepalive.KeepAliveCoordinator
import com.owner.mindbody.keepalive.KeepAliveStatus
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import com.owner.mindbody.util.CompanionDeviceHelper
import com.owner.mindbody.worker.BleSchedulerWorker
import com.owner.mindbody.worker.SyncWorker
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val UNLOCK_TAP_COUNT = 7
        private const val NTFY_TOPIC_PREFIX = "mindbody"
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

    val keepAliveStatus: StateFlow<KeepAliveStatus> = KeepAliveCoordinator.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KeepAliveStatus())

    val savedDeviceId: StateFlow<String?> = app.devicePreferences.savedDeviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionMode: StateFlow<ConnectionMode> = polar.connectionMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionMode.PERSISTENT)

    val bedtimeHour: StateFlow<Int> = app.devicePreferences.bedtimeHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicePreferences.DEFAULT_BEDTIME_HOUR)

    val wakeHour: StateFlow<Int> = app.devicePreferences.wakeHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicePreferences.DEFAULT_WAKE_HOUR)

    val bleNightlyScheduleEnabled: StateFlow<Boolean> = app.devicePreferences.bleNightlyScheduleEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DevicePreferences.DEFAULT_BLE_NIGHTLY_SCHEDULE_ENABLED
        )

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

    val ntfyTopic: StateFlow<String> = app.storage.syncPreferences.deviceId
        .map { id -> if (id.isBlank()) "" else "$NTFY_TOPIC_PREFIX-$id" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val sdkVersion: String = polar.sdkVersion()

    private var companionPromptDeviceId: String? = null

    val companionAssociated: StateFlow<Boolean> = app.devicePreferences.companionAssociationId
        .map { id -> id != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun scan() = polar.searchForDevices()

    fun connect(deviceId: String) {
        polar.connectToDevice(deviceId)
        markCompanionPromptCandidate(deviceId)
    }

    /**
     * 连接成功后若尚未 CDM 关联，返回 true 表示应弹出系统伴随设备 chooser。
     */
    fun shouldPromptCompanionAssociation(deviceId: String?): Boolean {
        if (!CompanionDeviceHelper.isSupported()) return false
        if (deviceId.isNullOrBlank()) return false
        if (CompanionDeviceHelper.isDeviceAssociated(app, deviceId)) return false
        return companionPromptDeviceId == deviceId
    }

    fun markCompanionPromptCandidate(deviceId: String) {
        if (CompanionDeviceHelper.isDeviceAssociated(app, deviceId)) {
            companionPromptDeviceId = null
        } else {
            companionPromptDeviceId = deviceId
        }
    }

    fun clearCompanionPrompt() {
        companionPromptDeviceId = null
    }

    fun startCompanionAssociation(
        activity: Activity,
        deviceId: String,
        onDeviceFound: (IntentSender) -> Unit,
    ) {
        if (!CompanionDeviceHelper.isSupported()) {
            Toast.makeText(app, "当前系统版本不支持伴随设备关联", Toast.LENGTH_SHORT).show()
            return
        }
        val mac = CompanionDeviceHelper.normalizeMacAddress(deviceId)
        CompanionDeviceHelper.associate(
            activity = activity,
            deviceMac = mac,
            onDeviceFound = onDeviceFound,
            onFailure = { error ->
                Toast.makeText(
                    app,
                    error ?: "伴随设备关联失败，可稍后在后台保活卡片重试",
                    Toast.LENGTH_SHORT,
                ).show()
                clearCompanionPrompt()
            },
        )
    }

    fun onCompanionAssociationComplete(deviceId: String, success: Boolean) {
        clearCompanionPrompt()
        if (!success) return
        viewModelScope.launch {
            val association = CompanionDeviceHelper.findAssociation(app, deviceId)
            val mac = CompanionDeviceHelper.normalizeMacAddress(deviceId)
            if (association != null) {
                app.devicePreferences.setCompanionAssociation(
                    associationId = association.id.coerceAtLeast(-1),
                    deviceMac = mac,
                )
                Toast.makeText(app, "已关联伴随设备，后台优先级已提升", Toast.LENGTH_SHORT).show()
            } else {
                // 用户可能选了设备但列表尚未刷新，仍记录 MAC
                if (mac != null) {
                    app.devicePreferences.setCompanionAssociation(
                        associationId = -1,
                        deviceMac = mac,
                    )
                }
                Toast.makeText(app, "伴随设备关联完成", Toast.LENGTH_SHORT).show()
            }
            refreshKeepAlive()
        }
    }

    fun refreshCompanionAssociationStatus() {
        viewModelScope.launch {
            val deviceId = savedDeviceId.value ?: connectedDeviceId.value ?: return@launch
            val association = CompanionDeviceHelper.findAssociation(app, deviceId)
            if (association != null) {
                app.devicePreferences.setCompanionAssociation(
                    associationId = association.id.coerceAtLeast(-1),
                    deviceMac = association.macAddress,
                )
            } else {
                app.devicePreferences.clearCompanionAssociation()
            }
        }
    }

    fun disconnect() = polar.disconnect()

    fun connectSaved() {
        viewModelScope.launch {
            val id = savedDeviceId.value
            if (!id.isNullOrBlank()) {
                markCompanionPromptCandidate(id)
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

    fun refreshKeepAlive() {
        refreshCompanionAssociationStatus()
        KeepAliveCoordinator.checkAndRecover(getApplication())
    }

    fun setConnectionMode(mode: ConnectionMode) = polar.setConnectionMode(mode)

    fun setBedtimeHour(hour: Int) {
        viewModelScope.launch {
            app.devicePreferences.setBedtimeHour(hour)
            if (app.devicePreferences.bleNightlyScheduleEnabled.first()) {
                BleSchedulerWorker.scheduleFromPreferences(
                    app,
                    ExistingWorkPolicy.REPLACE
                )
            }
        }
    }

    fun setWakeHour(hour: Int) {
        viewModelScope.launch {
            app.devicePreferences.setWakeHour(hour)
            if (app.devicePreferences.bleNightlyScheduleEnabled.first()) {
                BleSchedulerWorker.scheduleFromPreferences(
                    app,
                    ExistingWorkPolicy.REPLACE
                )
            }
        }
    }

    fun setBleNightlyScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.devicePreferences.setBleNightlyScheduleEnabled(enabled)
            if (enabled) {
                BleSchedulerWorker.scheduleFromPreferences(
                    app,
                    ExistingWorkPolicy.REPLACE
                )
            } else {
                BleSchedulerWorker.cancel(app)
            }
        }
    }

    fun setSyncBaseUrl(url: String) {
        viewModelScope.launch {
            app.storage.syncPreferences.setBaseUrl(url)
        }
    }

    fun setSyncApiKey(key: String) {
        viewModelScope.launch {
            app.storage.syncPreferences.setApiKey(key)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { app.storage.syncPreferences.setSyncEnabled(enabled) }
    }

    fun triggerSyncNow() {
        SyncWorker.enqueueOnce(app)
    }

    fun copyNtfyTopicToClipboard(context: Context) {
        val topic = ntfyTopic.value
        if (topic.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ntfy topic", topic))
        Toast.makeText(context, "已复制 $topic", Toast.LENGTH_SHORT).show()
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
