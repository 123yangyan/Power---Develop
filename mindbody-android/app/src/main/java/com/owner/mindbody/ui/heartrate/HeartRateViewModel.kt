package com.owner.mindbody.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.DailyHrStats
import com.owner.mindbody.data.local.HrSampleEntity
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.HrStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val hrRepository = app.storage.hr

    val currentHr: StateFlow<Int?> = app.polarBleManager.currentHr
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionState: StateFlow<ConnectionState> = app.polarBleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val todaySamples: StateFlow<List<HrSampleEntity>> = hrRepository.observeTodaySamples()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayStats = MutableStateFlow(DailyHrStats())
    val todayStats: StateFlow<DailyHrStats> = _todayStats.asStateFlow()

    init {
        viewModelScope.launch {
            todaySamples.collect {
                _todayStats.value = hrRepository.getTodayStats()
            }
        }
    }

    fun startBackgroundStream() {
        HrStreamService.start(getApplication())
    }

    fun stopBackgroundStream() {
        HrStreamService.stop(getApplication())
    }
}
