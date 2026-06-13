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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

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
    }

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
    }

    private fun scheduleSync(deviceId: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            syncMutex.withLock {
                runCatching { syncAll(deviceId) }
                    .onFailure { AppLogger.e(TAG, "Device sync failed", it) }
            }
        }
    }

    suspend fun syncAll(deviceId: String) {
        val polarApi = api ?: return
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
        if (from.isAfter(to)) return
        val sessions = api.getSleep(deviceId, from, to).mapNotNull { PolarDeviceDataMappers.mapSleep(it) }
        storage.sleep.upsertAll(sessions)
        syncPreferences.setLastSyncedDate(SyncDataType.SLEEP, to)
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
