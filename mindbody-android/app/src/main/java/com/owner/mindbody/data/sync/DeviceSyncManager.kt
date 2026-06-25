package com.owner.mindbody.data.sync

import com.owner.mindbody.data.DeviceSyncPreferences
import com.owner.mindbody.data.SyncDataType
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.util.AppLogger
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.impl.utils.CaloriesType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/** 设备离线数据同步状态 */
enum class DeviceSyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED
}

/**
 * 设备离线数据同步：在 BLE Feature 就绪后，从 Polar Loop 拉取活动/睡眠/训练等数据并落库。
 */
class DeviceSyncManager(
    private val storage: AppStorage,
    private val syncPreferences: DeviceSyncPreferences
) {
    companion object {
        private const val TAG = "DeviceSyncManager"
        private const val DEFAULT_LOOKBACK_DAYS = 7L
        /** 睡眠数据滚动重拉窗口（含 today 共 3 天）。 */
        private const val SLEEP_ROLLING_DAYS = 2L
    }

    private val _syncStatus = MutableStateFlow(DeviceSyncStatus.IDLE)
    /** 当前同步状态，UI 层可观察以展示同步进度/错误提示。 */
    val syncStatus: StateFlow<DeviceSyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    /** 最近一次同步错误信息（成功时置 null）。 */
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var api: PolarBleApi? = null
    private var syncJob: Job? = null

    private var activityReady = false
    private var sleepReady = false
    private var trainingReady = false

    fun attachApi(polarApi: PolarBleApi) {
        api = polarApi
    }

    fun onFeatureReady(deviceId: String, feature: PolarBleApi.PolarBleSdkFeature) {
        when (feature) {
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> activityReady = true
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> sleepReady = true
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> trainingReady = true
            else -> return
        }
        scheduleSync(deviceId)
    }

    fun resetFeatureFlags() {
        activityReady = false
        sleepReady = false
        trainingReady = false
        _syncStatus.value = DeviceSyncStatus.IDLE
    }

    fun shutdown() {
        syncJob?.cancel()
        _syncStatus.value = DeviceSyncStatus.IDLE
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun scheduleSync(deviceId: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            syncMutex.withLock {
                _syncStatus.value = DeviceSyncStatus.SYNCING
                _lastSyncError.value = null
                runCatching { syncAll(deviceId) }
                    .onSuccess { completed ->
                        if (completed) {
                            _syncStatus.value = DeviceSyncStatus.SUCCESS
                            AppLogger.i(TAG, "Device sync completed successfully")
                        } else {
                            _syncStatus.value = DeviceSyncStatus.IDLE
                        }
                    }
                    .onFailure {
                        _syncStatus.value = DeviceSyncStatus.FAILED
                        _lastSyncError.value = it.message
                        AppLogger.e(TAG, "Device sync failed", it)
                    }
            }
        }
    }

    suspend fun syncAll(deviceId: String): Boolean {
        val polarApi = api ?: return false

        // 1. 通知设备准备数据同步（将缓存数据刷入文件、进入高速传输模式）
        //    参见 Polar SDK SyncImplementationGuideline: 必须在读取数据前调用此方法
        try {
            val ready = polarApi.sendInitializationAndStartSyncNotifications(deviceId)
            if (!ready) {
                AppLogger.w(TAG, "Device not ready for sync — device busy or training in progress")
                return false
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to init sync notifications, continuing anyway", e)
        }

        try {
            // 2. 同步设备时间，确保按日期查询的数据范围准确
            //    参见 SyncImplementationGuideline: "Check that device clock is up-to-date"
            try {
                polarApi.setLocalTime(deviceId, LocalDateTime.now())
                AppLogger.d(TAG, "Device time synced")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to set device time", e)
            }

            // 3. 按 SDK Feature 就绪情况分阶段拉取数据
            if (activityReady) {
                syncActivityData(polarApi, deviceId)
            }
            if (sleepReady) {
                syncSleepData(polarApi, deviceId)
            }
            if (trainingReady) {
                syncTrainingData(polarApi, deviceId)
            }
            storage.flushAll()
        } finally {
            // 4. 确保无论成功失败都退出同步模式，让设备回到正常功耗状态
            try {
                polarApi.sendTerminateAndStopSyncNotifications(deviceId)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to stop sync notifications", e)
            }
        }
        return true
    }

    private suspend fun syncActivityData(api: PolarBleApi, deviceId: String) {
        val (from, to) = resolveDateRange(SyncDataType.ACTIVITY_DAY)
        if (from.isAfter(to)) return

        val stepsMap = api.getSteps(deviceId, from, to)
            .mapNotNull { item -> item.date?.toString()?.let { it to item.steps } }
            .toMap()
        val activeMap = api.getActiveTime(deviceId, from, to)
            .associate { it.date.toString() to PolarDeviceDataMappers.activeTimeToMinutes(it) }
        val activityCalMap = api.getCalories(deviceId, from, to, CaloriesType.ACTIVITY)
            .mapNotNull { item -> item.date?.toString()?.let { it to item.calories } }
            .toMap()
        val trainingCalMap = api.getCalories(deviceId, from, to, CaloriesType.TRAINING)
            .mapNotNull { item -> item.date?.toString()?.let { it to item.calories } }
            .toMap()
        val bmrCalMap = api.getCalories(deviceId, from, to, CaloriesType.BMR)
            .mapNotNull { item -> item.date?.toString()?.let { it to item.calories } }
            .toMap()

        val allDates = (stepsMap.keys + activeMap.keys + activityCalMap.keys).toSet()
        val summaries = allDates.map { date ->
            PolarDeviceDataMappers.mergeActivityDaySummary(
                date = date,
                steps = stepsMap[date],
                activeTimeMinutes = activeMap[date],
                caloriesActivity = activityCalMap[date],
                caloriesTraining = trainingCalMap[date],
                caloriesBmr = bmrCalMap[date]
            )
        }
        storage.activityDay.upsertAll(summaries)
        syncPreferences.setLastSyncedDate(SyncDataType.ACTIVITY_DAY, to)

        val hr247 = api.get247HrSamples(deviceId, from, to).flatMap { PolarDeviceDataMappers.map247Hr(it) }
        storage.hr247.saveAll(hr247)
        syncPreferences.setLastSyncedDate(SyncDataType.HR_247, to)

        val ppi247 = api.get247PPiSamples(deviceId, from, to).flatMap { PolarDeviceDataMappers.map247Ppi(it) }
        storage.ppi247.saveAll(ppi247)
        syncPreferences.setLastSyncedDate(SyncDataType.PPI_247, to)

        val skinTemp247 = api.getSkinTemperature(deviceId, from, to).flatMap { PolarDeviceDataMappers.mapSkinTemp247(it) }
        storage.skinTemp247.saveAll(skinTemp247)
        syncPreferences.setLastSyncedDate(SyncDataType.SKIN_TEMP_247, to)

        val nightly = api.getNightlyRecharge(deviceId, from, to).mapNotNull { PolarDeviceDataMappers.mapNightlyRecharge(it) }
        storage.nightlyRecharge.upsertAll(nightly)
        syncPreferences.setLastSyncedDate(SyncDataType.NIGHTLY_RECHARGE, to)

        val minuteSamples = api.getActivitySampleData(deviceId, from, to).flatMap { PolarDeviceDataMappers.mapActivityMinute(it) }
        storage.activityMinute.saveAll(minuteSamples)
        syncPreferences.setLastSyncedDate(SyncDataType.ACTIVITY_MINUTE, to)
    }

    private suspend fun syncSleepData(api: PolarBleApi, deviceId: String) {
        val (from, to) = resolveDateRange(SyncDataType.SLEEP)
        val rollingFrom = to.minusDays(SLEEP_ROLLING_DAYS)
        val effectiveFrom = if (from.isAfter(rollingFrom)) rollingFrom else from
        if (effectiveFrom.isAfter(to)) return

        AppLogger.d(TAG, "Sleep sync range: $effectiveFrom .. $to (cursor from=$from)")
        val sessions = api.getSleep(deviceId, effectiveFrom, to).mapNotNull { PolarDeviceDataMappers.mapSleep(it) }
        storage.sleep.upsertAllMerge(sessions)

        val hasValidTimestamps = sessions.any {
            it.sleepStartTimeMs != null || it.sleepEndTimeMs != null
        }
        if (hasValidTimestamps) {
            syncPreferences.setLastSyncedDate(SyncDataType.SLEEP, to)
            AppLogger.d(TAG, "Sleep sync: ${sessions.size} sessions, cursor advanced to $to")
        } else {
            AppLogger.d(TAG, "Sleep sync: no valid timestamps, keeping cursor")
        }
    }

    private suspend fun syncTrainingData(api: PolarBleApi, deviceId: String) {
        val (from, to) = resolveDateRange(SyncDataType.TRAINING)
        val knownPaths = storage.training.getAllDevicePaths().toSet()
        val references = api.getTrainingSessionReferences(deviceId, from, to).toList()
        val entities = references.mapNotNull { reference ->
            if (knownPaths.contains(reference.path)) return@mapNotNull null
            runCatching {
                val session = api.getTrainingSession(deviceId, reference)
                PolarDeviceDataMappers.enrichTrainingSession(reference, session)
            }.getOrElse {
                AppLogger.w(TAG, "Training session fetch failed for ${reference.path}", it)
                PolarDeviceDataMappers.mapTrainingReference(reference)
            }
        }
        storage.training.upsertAll(entities)
        syncPreferences.setLastSyncedDate(SyncDataType.TRAINING, to)
    }

    private suspend fun resolveDateRange(type: SyncDataType): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val lastSynced = syncPreferences.getLastSyncedDate(type)
        val from = lastSynced?.plusDays(1) ?: today.minusDays(DEFAULT_LOOKBACK_DAYS)
        return from to today
    }
}
