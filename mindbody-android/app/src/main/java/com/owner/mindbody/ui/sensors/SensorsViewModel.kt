package com.owner.mindbody.ui.sensors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.polar.AccSample
import com.owner.mindbody.polar.ConnectionState
import com.polar.sdk.api.model.PolarPpiData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SensorsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication

    val connectionState: StateFlow<ConnectionState> = app.polarBleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val currentAcc: StateFlow<AccSample?> = app.polarBleManager.currentAcc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestPpi: StateFlow<PolarPpiData.PolarPpiSample?> = app.polarBleManager.latestPpi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
