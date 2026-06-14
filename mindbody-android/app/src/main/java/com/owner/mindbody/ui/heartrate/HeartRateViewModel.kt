package com.owner.mindbody.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.DailyHrStats
import com.owner.mindbody.data.local.Hr247SampleEntity
import com.owner.mindbody.data.local.HrSampleEntity
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.HrStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val hrRepository = app.storage.hr
    private val hr247Repository = app.storage.hr247

    val currentHr: StateFlow<Int?> = app.polarBleManager.currentHr
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSkinTemp: StateFlow<Float?> = app.polarBleManager.currentSkinTemp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionState: StateFlow<ConnectionState> = app.polarBleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    /** 实时流 + 24/7 离线样本合并，供图表无缝展示 */
    val todaySamples: StateFlow<List<HrSampleEntity>> = combine(
        hrRepository.observeTodaySamples(),
        hr247Repository.observeTodaySamples()
    ) { live, offline ->
        mergeHrSamples(live, offline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /**
     * 合并在线实时样本与 24/7 离线样本。
     * 同一 10 秒桶内优先保留实时流（精度更高），离线数据填补断联空白。
     */
    private fun mergeHrSamples(
        live: List<HrSampleEntity>,
        offline: List<Hr247SampleEntity>
    ): List<HrSampleEntity> {
        val bucketMs = MERGE_BUCKET_SECONDS * 1000L
        val liveBuckets = live.map { it.timestamp / bucketMs }.toSet()
        val offlineOnly = offline
            .filter { it.timestamp / bucketMs !in liveBuckets }
            .map { sample ->
                HrSampleEntity(
                    timestamp = sample.timestamp,
                    bpm = sample.bpm,
                    rrMs = null
                )
            }
        return (live + offlineOnly).sortedBy { it.timestamp }
    }

    companion object {
        private const val MERGE_BUCKET_SECONDS = 10L
    }
}
