package com.owner.mindbody.ui.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val polar = app.polarBleManager

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
}
