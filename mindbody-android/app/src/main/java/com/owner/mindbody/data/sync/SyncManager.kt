package com.owner.mindbody.data.sync

import com.owner.mindbody.data.local.*
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.util.AppLogger

/**
 * 云端同步引擎：遍历所有 13 个仓库，批量上报未同步数据。
 *
 * 不用泛型循环避免 Kotlin 类型推导问题 — 每个仓库显式调用。
 */
class SyncManager(
    private val storage: AppStorage,
    private val syncPreferences: SyncPreferences
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val BATCH_SIZE = 500
    }

    var apiClient: SyncApiClient? = null

    data class SyncResult(
        val uploaded: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val error: String? = null
    )

    suspend fun syncOnce(): SyncResult {
        val client = apiClient ?: return SyncResult(error = "API client not configured")
        val deviceId = syncPreferences.getOrCreateDeviceId()

        var up = 0; var fail = 0; var skip = 0

        // ── A 组：SyncableDao<Long> ──

        // hr
        runSyncLoop(client, deviceId, "LIVE_STREAM", "hr_samples") {
            storage.hr.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // skinTemp
        runSyncLoop(client, deviceId, "LIVE_STREAM", "skin_temp_samples") {
            storage.skinTemp.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // acc 10s summary
        runSyncLoop(client, deviceId, "LIVE_STREAM", "acc_minute_summary") {
            storage.acc.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // ppi
        runSyncLoop(client, deviceId, "LIVE_STREAM", "ppi_samples") {
            storage.ppi.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // hr247
        runSyncLoop(client, deviceId, "OFFLINE_SYNC", "hr_247_samples") {
            storage.hr247.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // ppi247
        runSyncLoop(client, deviceId, "OFFLINE_SYNC", "ppi_247_samples") {
            storage.ppi247.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // skinTemp247
        runSyncLoop(client, deviceId, "OFFLINE_SYNC", "skin_temp_247_samples") {
            storage.skinTemp247.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // activityMinute
        runSyncLoop(client, deviceId, "OFFLINE_SYNC", "activity_minute_samples") {
            storage.activityMinute.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // mood
        runSyncLoop(client, deviceId, "USER_INPUT", "mood_entries") {
            storage.mood.getUnsynced(BATCH_SIZE)
        }.apply { up += first; fail += second; skip += third }

        // ── B 组：String-key DAO ──
        suspend fun runB(table: String, source: String,
                 getter: suspend (Int) -> List<Any>,
                 mark: suspend (List<String>) -> Unit) {
            while (true) {
                val batch = getter(BATCH_SIZE)
                if (batch.isEmpty()) break
                val rows = batch.map { entityToMap(it, table) }
                val r = client.uploadBatch(deviceId, source, table, columnsFor(table), rows)
                if (r.error != null) { fail += batch.size; AppLogger.w(TAG, "Sync $table failed: ${r.error}"); break }
                if (r.inserted == 0) {
                    fail += batch.size
                    AppLogger.w(TAG, "Sync $table: server inserted=0 skipped=${r.skipped}, not marking SYNCED")
                    break
                }
                val keys = batch.map { entityStringKey(it, table) }
                mark(keys)
                up += r.inserted; skip += r.skipped
            }
        }

        runB("activity_day_summary", "OFFLINE_SYNC",
            { storage.activityDay.getUnsynced(it) },
            { storage.activityDay.markSynced(it) })
        runB("nightly_recharge", "OFFLINE_SYNC",
            { storage.nightlyRecharge.getUnsynced(it) },
            { storage.nightlyRecharge.markSynced(it) })
        runB("sleep_sessions", "OFFLINE_SYNC",
            { storage.sleep.getUnsynced(it) },
            { storage.sleep.markSynced(it) })
        runB("training_sessions", "OFFLINE_SYNC",
            { storage.training.getUnsynced(it) },
            { storage.training.markSynced(it) })

        val summary = "uploaded=$up failed=$fail skipped=$skip"
        syncPreferences.setLastSyncResult(System.currentTimeMillis(), summary)
        AppLogger.i(TAG, "syncOnce done: $summary")
        return SyncResult(uploaded = up, failed = fail, skipped = skip)
    }

    // ── A 组 Long-key 同步循环 ──
    private suspend fun <T : Any> runSyncLoop(
        client: SyncApiClient,
        deviceId: String,
        source: String,
        table: String,
        getBatch: suspend (Int) -> List<T>
    ): Triple<Int, Int, Int> {
        var up = 0; var fail = 0; var skip = 0
        while (true) {
            val batch = getBatch(BATCH_SIZE)
            if (batch.isEmpty()) break
            val rows = batch.map { entityToMap(it, table) }
            val r = client.uploadBatch(deviceId, source, table, columnsFor(table), rows)
            if (r.error != null) {
                fail += batch.size
                markFailedBatch(batch, table)
                AppLogger.w(TAG, "Sync $table failed: ${r.error}")
                break
            }
            if (r.inserted == 0) {
                fail += batch.size
                markFailedBatch(batch, table)
                AppLogger.w(TAG, "Sync $table: server inserted=0 skipped=${r.skipped}, not marking SYNCED")
                break
            }
            markSyncedBatch(batch, table)
            up += r.inserted; skip += r.skipped
        }
        return Triple(up, fail, skip)
    }

    private suspend fun markSyncedBatch(batch: List<Any>, table: String) {
        val ids = batch.mapNotNull { entityIdOrNull(it, table) }
        if (ids.isEmpty()) return
        when (table) {
            "hr_samples" -> storage.hr.markSynced(ids)
            "skin_temp_samples" -> storage.skinTemp.markSynced(ids)
            "acc_minute_summary" -> storage.acc.markSynced(ids)
            "ppi_samples" -> storage.ppi.markSynced(ids)
            "hr_247_samples" -> storage.hr247.markSynced(ids)
            "ppi_247_samples" -> storage.ppi247.markSynced(ids)
            "skin_temp_247_samples" -> storage.skinTemp247.markSynced(ids)
            "activity_minute_samples" -> storage.activityMinute.markSynced(ids)
            "mood_entries" -> storage.mood.markSynced(ids)
        }
    }

    private suspend fun markFailedBatch(batch: List<Any>, table: String) {
        val ids = batch.mapNotNull { entityIdOrNull(it, table) }
        if (ids.isEmpty()) return
        when (table) {
            "hr_samples" -> storage.hr.markFailed(ids)
            "skin_temp_samples" -> storage.skinTemp.markFailed(ids)
            "acc_minute_summary" -> storage.acc.markFailed(ids)
            "ppi_samples" -> storage.ppi.markFailed(ids)
            "hr_247_samples" -> storage.hr247.markFailed(ids)
            "ppi_247_samples" -> storage.ppi247.markFailed(ids)
            "skin_temp_247_samples" -> storage.skinTemp247.markFailed(ids)
            "activity_minute_samples" -> storage.activityMinute.markFailed(ids)
            "mood_entries" -> storage.mood.markFailed(ids)
        }
    }

    private fun entityIdOrNull(entity: Any, table: String): Long? {
        val id = when (entity) {
            is HrSampleEntity -> entity.id
            is SkinTempSampleEntity -> entity.id
            is AccMinuteSummaryEntity -> entity.id
            is PpiSampleEntity -> entity.id
            is Hr247SampleEntity -> entity.id
            is Ppi247SampleEntity -> entity.id
            is SkinTemp247SampleEntity -> entity.id
            is ActivityMinuteSampleEntity -> entity.id
            is MoodEntryEntity -> entity.id
            else -> return null
        }
        return id
    }

    private fun entityStringKey(entity: Any, table: String): String = when (entity) {
        is ActivityDaySummaryEntity -> entity.date
        is NightlyRechargeEntity -> entity.date
        is SleepSessionEntity -> entity.date
        is TrainingSessionEntity -> entity.devicePath
        else -> entity.hashCode().toString()
    }

    private fun columnsFor(table: String): List<String> = when (table) {
        "hr_samples" -> listOf("bpm", "rr_ms")
        "skin_temp_samples" -> listOf("temperature_c")
        "acc_minute_summary" -> listOf("avg_magnitude_mg", "max_magnitude_mg", "sample_count")
        "ppi_samples" -> listOf("ppi_ms", "error_estimate_ms", "hr_bpm", "blocker_bit", "skin_contact_supported", "skin_contact_status")
        "activity_day_summary" -> listOf("steps", "active_time_minutes", "calories_total", "calories_activity", "calories_training", "calories_bmr")
        "hr_247_samples" -> listOf("bpm", "trigger_type")
        "ppi_247_samples" -> listOf("ppi_ms", "error_estimate_ms", "trigger_type", "skin_contact", "movement")
        "skin_temp_247_samples" -> listOf("temperature_c")
        "nightly_recharge" -> listOf("ans_charge_percent", "recovery_indicator", "ans_rate", "hr_mean_bpm", "hr_min_bpm", "rr_mean_ms", "breathing_rate_hz", "sleep_tip", "vitality_tip", "exercise_tip")
        "activity_minute_samples" -> listOf("steps", "met_x100", "activity_level")
        "sleep_sessions" -> listOf("sleep_start_time_ms", "sleep_end_time_ms", "sleep_goal_minutes", "user_sleep_rating", "battery_ran_out", "sleep_skin_temp_celsius", "sleep_skin_temp_deviation", "sleep_wake_phases_json", "sleep_cycles_json")
        "training_sessions" -> listOf("session_date", "file_size_bytes", "exercise_count", "start_time_ms", "end_time_ms", "duration_seconds")
        "mood_entries" -> listOf("fact", "coord_x", "coord_y", "occurred_at", "hr_at_entry", "role_id")
        else -> emptyList()
    }

    private fun parseDateToEpochMs(date: String): Long {
        return runCatching {
            java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun entityToMap(entity: Any, table: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["clientRowId"] = when (entity) {
            is HrSampleEntity -> entity.id.toString()
            is SkinTempSampleEntity -> entity.id.toString()
            is AccMinuteSummaryEntity -> entity.id.toString()
            is PpiSampleEntity -> entity.id.toString()
            is Hr247SampleEntity -> entity.id.toString()
            is Ppi247SampleEntity -> entity.id.toString()
            is SkinTemp247SampleEntity -> entity.id.toString()
            is ActivityMinuteSampleEntity -> entity.id.toString()
            is ActivityDaySummaryEntity -> entity.date
            is NightlyRechargeEntity -> entity.date
            is SleepSessionEntity -> entity.date
            is TrainingSessionEntity -> entity.devicePath
            is MoodEntryEntity -> entity.id.toString()
            else -> entity.hashCode().toString()
        }
        map["ts"] = when (entity) {
            is HrSampleEntity -> entity.timestamp
            is SkinTempSampleEntity -> entity.timestamp
            is AccMinuteSummaryEntity -> entity.minuteTimestamp
            is PpiSampleEntity -> entity.timestamp
            is Hr247SampleEntity -> entity.timestamp
            is Ppi247SampleEntity -> entity.timestamp
            is SkinTemp247SampleEntity -> entity.timestamp
            is ActivityMinuteSampleEntity -> entity.timestamp
            is ActivityDaySummaryEntity -> parseDateToEpochMs(entity.date)
            is NightlyRechargeEntity -> parseDateToEpochMs(entity.date)
            is SleepSessionEntity -> entity.sleepStartTimeMs ?: 0L
            is TrainingSessionEntity -> entity.startTimeMs ?: 0L
            is MoodEntryEntity -> entity.occurredAt
            else -> 0L
        }

        for (col in columnsFor(table)) {
            map[col] = when (col) {
                "bpm" -> (entity as? HrSampleEntity)?.bpm ?: (entity as? Hr247SampleEntity)?.bpm
                "rr_ms" -> (entity as? HrSampleEntity)?.rrMs
                "temperature_c" -> (entity as? SkinTempSampleEntity)?.temperatureC
                    ?: (entity as? SkinTemp247SampleEntity)?.temperatureC
                "avg_magnitude_mg" -> (entity as? AccMinuteSummaryEntity)?.avgMagnitudeMg
                "max_magnitude_mg" -> (entity as? AccMinuteSummaryEntity)?.maxMagnitudeMg
                "sample_count" -> (entity as? AccMinuteSummaryEntity)?.sampleCount
                "ppi_ms" -> (entity as? PpiSampleEntity)?.ppiMs ?: (entity as? Ppi247SampleEntity)?.ppiMs
                "error_estimate_ms" -> (entity as? PpiSampleEntity)?.errorEstimateMs ?: (entity as? Ppi247SampleEntity)?.errorEstimateMs
                "hr_bpm" -> (entity as? PpiSampleEntity)?.hrBpm
                "blocker_bit" -> (entity as? PpiSampleEntity)?.blockerBit
                "skin_contact_supported" -> (entity as? PpiSampleEntity)?.skinContactSupported
                "skin_contact_status" -> (entity as? PpiSampleEntity)?.skinContactStatus
                "trigger_type" -> (entity as? Hr247SampleEntity)?.triggerType ?: (entity as? Ppi247SampleEntity)?.triggerType
                "skin_contact" -> (entity as? Ppi247SampleEntity)?.skinContact
                "movement" -> (entity as? Ppi247SampleEntity)?.movement
                "steps" -> (entity as? ActivityDaySummaryEntity)?.steps ?: (entity as? ActivityMinuteSampleEntity)?.steps
                "active_time_minutes" -> (entity as? ActivityDaySummaryEntity)?.activeTimeMinutes
                "calories_total" -> (entity as? ActivityDaySummaryEntity)?.caloriesTotal
                "calories_activity" -> (entity as? ActivityDaySummaryEntity)?.caloriesActivity
                "calories_training" -> (entity as? ActivityDaySummaryEntity)?.caloriesTraining
                "calories_bmr" -> (entity as? ActivityDaySummaryEntity)?.caloriesBmr
                "met_x100" -> (entity as? ActivityMinuteSampleEntity)?.metX100
                "activity_level" -> (entity as? ActivityMinuteSampleEntity)?.activityLevel
                "ans_charge_percent" -> (entity as? NightlyRechargeEntity)?.ansChargePercent
                "recovery_indicator" -> (entity as? NightlyRechargeEntity)?.recoveryIndicator
                "ans_rate" -> (entity as? NightlyRechargeEntity)?.ansRate
                "hr_mean_bpm" -> (entity as? NightlyRechargeEntity)?.hrMeanBpm
                "hr_min_bpm" -> (entity as? NightlyRechargeEntity)?.hrMinBpm
                "rr_mean_ms" -> (entity as? NightlyRechargeEntity)?.rrMeanMs
                "breathing_rate_hz" -> (entity as? NightlyRechargeEntity)?.breathingRateHz
                "sleep_tip" -> (entity as? NightlyRechargeEntity)?.sleepTip
                "vitality_tip" -> (entity as? NightlyRechargeEntity)?.vitalityTip
                "exercise_tip" -> (entity as? NightlyRechargeEntity)?.exerciseTip
                "sleep_start_time_ms" -> (entity as? SleepSessionEntity)?.sleepStartTimeMs
                "sleep_end_time_ms" -> (entity as? SleepSessionEntity)?.sleepEndTimeMs
                "sleep_goal_minutes" -> (entity as? SleepSessionEntity)?.sleepGoalMinutes
                "user_sleep_rating" -> (entity as? SleepSessionEntity)?.userSleepRating
                "battery_ran_out" -> (entity as? SleepSessionEntity)?.batteryRanOut
                "sleep_skin_temp_celsius" -> (entity as? SleepSessionEntity)?.sleepSkinTempCelsius
                "sleep_skin_temp_deviation" -> (entity as? SleepSessionEntity)?.sleepSkinTempDeviation
                "sleep_wake_phases_json" -> (entity as? SleepSessionEntity)?.sleepWakePhasesJson
                "sleep_cycles_json" -> (entity as? SleepSessionEntity)?.sleepCyclesJson
                "session_date" -> (entity as? TrainingSessionEntity)?.sessionDate
                "file_size_bytes" -> (entity as? TrainingSessionEntity)?.fileSizeBytes
                "exercise_count" -> (entity as? TrainingSessionEntity)?.exerciseCount
                "start_time_ms" -> (entity as? TrainingSessionEntity)?.startTimeMs
                "end_time_ms" -> (entity as? TrainingSessionEntity)?.endTimeMs
                "duration_seconds" -> (entity as? TrainingSessionEntity)?.durationSeconds
                "fact" -> (entity as? MoodEntryEntity)?.fact
                "coord_x" -> (entity as? MoodEntryEntity)?.coordX
                "coord_y" -> (entity as? MoodEntryEntity)?.coordY
                "occurred_at" -> (entity as? MoodEntryEntity)?.occurredAt
                "hr_at_entry" -> (entity as? MoodEntryEntity)?.hrAtEntry
                "role_id" -> (entity as? MoodEntryEntity)?.roleId
                else -> null
            }
        }
        return map
    }

}
