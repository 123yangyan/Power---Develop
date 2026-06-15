package com.owner.mindbody.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.DailyHrStats
import com.owner.mindbody.data.HrvUtils
import com.owner.mindbody.data.RrIntervalSample
import com.owner.mindbody.data.local.ActivityMinuteSampleEntity
import com.owner.mindbody.data.local.Hr247SampleEntity
import com.owner.mindbody.data.local.HrSampleEntity
import com.owner.mindbody.data.local.Ppi247SampleEntity
import com.owner.mindbody.data.local.PpiSampleEntity
import com.owner.mindbody.data.local.SkinTemp247SampleEntity
import com.owner.mindbody.data.local.SkinTempSampleEntity
import com.owner.mindbody.data.local.TrainingSessionEntity
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.HrStreamService
import com.owner.mindbody.ui.components.ChartExerciseBand
import com.owner.mindbody.ui.components.ChartValuePoint
import com.owner.mindbody.ui.components.ChartWindowPreset
import com.owner.mindbody.ui.components.MindBodyChartState
import com.owner.mindbody.ui.components.SplineChartUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val hrRepository = app.storage.hr
    private val hr247Repository = app.storage.hr247
    private val zoneId = ZoneId.systemDefault()
    private val todayStartMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val todayEndMs = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val _windowPreset = MutableStateFlow(ChartWindowPreset.ONE_HOUR)
    private val _windowStartMs = MutableStateFlow(
        (System.currentTimeMillis() - ChartWindowPreset.ONE_HOUR.durationMs).coerceAtLeast(todayStartMs)
    )

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

    private val tempSamples = combine(
        app.storage.skinTemp.observeBetween(todayStartMs, todayEndMs),
        app.storage.skinTemp247.observeBetween(todayStartMs, todayEndMs)
    ) { live, offline ->
        mergeSkinTempSamples(live, offline)
    }

    private val ppiSamples = combine(
        app.storage.ppi.observeBetween(todayStartMs, todayEndMs),
        app.storage.ppi247.observeBetween(todayStartMs, todayEndMs)
    ) { live, offline ->
        mergePpiSamples(live, offline)
    }

    private val chartData = combine(
        todaySamples,
        tempSamples,
        ppiSamples,
        app.storage.activityMinute.observeBetween(todayStartMs, todayEndMs),
        app.storage.training.observeSessionsBetween(todayStartMs, todayEndMs)
    ) { hr, temp, ppi, activity, training ->
        ChartData(
            hrPoints = hr.map { ChartValuePoint(it.timestamp, it.bpm.toFloat()) },
            tempPoints = temp.map { ChartValuePoint(it.timestamp, it.temperatureC) },
            hrvPoints = HrvUtils.computeRmssd(ppi).map { ChartValuePoint(it.timestampMs, it.rmssdMs) },
            activityPoints = activity.mapNotNull { it.toActivityPoint() },
            exerciseBands = training.mapNotNull { it.toExerciseBand() }
        )
    }.flowOn(Dispatchers.Default)

    val chartState: StateFlow<MindBodyChartState> = combine(
        chartData,
        _windowPreset,
        _windowStartMs
    ) { data, preset, requestedStart ->
        val now = System.currentTimeMillis()
        val window = SplineChartUtils.clampTimeWindow(
            requestedStartMs = requestedStart,
            durationMs = preset.durationMs,
            minStartMs = todayStartMs,
            maxEndMs = now.coerceAtLeast(todayStartMs + 1)
        )
        MindBodyChartState(
            hrPoints = SplineChartUtils.downsampleValues(data.hrPoints, preset.bucketMs),
            tempPoints = SplineChartUtils.downsampleValues(data.tempPoints, preset.bucketMs),
            hrvPoints = SplineChartUtils.downsampleValues(data.hrvPoints, preset.bucketMs),
            activityPoints = SplineChartUtils.downsampleValues(data.activityPoints, preset.bucketMs),
            exerciseBands = data.exerciseBands,
            window = window,
            preset = preset,
            todayStartMs = todayStartMs,
            nowMs = now
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialChartState()
        )

    private val _todayStats = MutableStateFlow(DailyHrStats())
    val todayStats: StateFlow<DailyHrStats> = _todayStats.asStateFlow()

    init {
        viewModelScope.launch {
            todaySamples.collect {
                _todayStats.value = withContext(Dispatchers.IO) {
                    hrRepository.getTodayStats()
                }
            }
        }
    }

    fun startBackgroundStream() {
        HrStreamService.start(getApplication())
    }

    fun stopBackgroundStream() {
        HrStreamService.stop(getApplication())
    }

    fun setChartPreset(preset: ChartWindowPreset) {
        _windowPreset.value = preset
        _windowStartMs.value = (System.currentTimeMillis() - preset.durationMs).coerceAtLeast(todayStartMs)
    }

    fun panChartWindow(deltaMs: Long) {
        _windowStartMs.value += deltaMs
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

    private fun mergeSkinTempSamples(
        live: List<SkinTempSampleEntity>,
        offline: List<SkinTemp247SampleEntity>
    ): List<SkinTempSampleEntity> {
        val bucketMs = TEMP_MERGE_BUCKET_MS
        val liveBuckets = live.map { it.timestamp / bucketMs }.toSet()
        val offlineOnly = offline
            .filter { it.timestamp / bucketMs !in liveBuckets }
            .map { SkinTempSampleEntity(timestamp = it.timestamp, temperatureC = it.temperatureC) }
        return (live + offlineOnly).sortedBy { it.timestamp }
    }

    private fun mergePpiSamples(
        live: List<PpiSampleEntity>,
        offline: List<Ppi247SampleEntity>
    ): List<RrIntervalSample> {
        val bucketMs = PPI_MERGE_BUCKET_MS
        val liveBuckets = live.map { it.timestamp / bucketMs }.toSet()
        val liveIntervals = live.map { RrIntervalSample(it.timestamp, it.ppiMs) }
        val offlineOnly = offline
            .filter { it.timestamp / bucketMs !in liveBuckets }
            .map { RrIntervalSample(it.timestamp, it.ppiMs) }
        return (liveIntervals + offlineOnly).sortedBy { it.timestampMs }
    }

    private fun ActivityMinuteSampleEntity.toActivityPoint(): ChartValuePoint? {
        val value = when {
            metX100 != null -> metX100 / 100f
            activityLevel != null -> activityLevel.toFloat()
            steps != null -> steps.toFloat()
            else -> null
        } ?: return null
        return ChartValuePoint(timestamp, value)
    }

    private fun TrainingSessionEntity.toExerciseBand(): ChartExerciseBand? {
        val start = startTimeMs ?: return null
        val end = endTimeMs ?: return null
        return ChartExerciseBand(startMs = start, endMs = end, label = "运动")
    }

    private fun initialChartState(): MindBodyChartState {
        val now = System.currentTimeMillis()
        val window = SplineChartUtils.clampTimeWindow(
            requestedStartMs = (now - ChartWindowPreset.ONE_HOUR.durationMs).coerceAtLeast(todayStartMs),
            durationMs = ChartWindowPreset.ONE_HOUR.durationMs,
            minStartMs = todayStartMs,
            maxEndMs = now.coerceAtLeast(todayStartMs + 1)
        )
        return MindBodyChartState(
            window = window,
            preset = ChartWindowPreset.ONE_HOUR,
            todayStartMs = todayStartMs,
            nowMs = now
        )
    }

    private data class ChartData(
        val hrPoints: List<ChartValuePoint>,
        val tempPoints: List<ChartValuePoint>,
        val hrvPoints: List<ChartValuePoint>,
        val activityPoints: List<ChartValuePoint>,
        val exerciseBands: List<ChartExerciseBand>
    )

    companion object {
        private const val MERGE_BUCKET_SECONDS = 10L
        private const val TEMP_MERGE_BUCKET_MS = 5 * 60_000L
        private const val PPI_MERGE_BUCKET_MS = 60_000L
    }
}
