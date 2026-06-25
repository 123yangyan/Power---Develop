package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SleepSessionEntity
import com.owner.mindbody.data.local.SyncState

class SleepRepository(private val database: AppDatabase) {

    private val dao = database.sleepSessionDao()

    suspend fun upsertAll(entities: List<SleepSessionEntity>) {
        upsertAllMerge(entities)
    }

    /** 合并 upsert：新数据 timestamp 为 null 时保留已有非 null 字段。 */
    suspend fun upsertAllMerge(entities: List<SleepSessionEntity>) {
        if (entities.isEmpty()) return
        val existing = dao.getByDates(entities.map { it.date }).associateBy { it.date }
        val merged = entities.map { mergeSleep(existing[it.date], it) }
        dao.upsertAll(merged)
    }

    suspend fun getUnsynced(limit: Int = 500) = dao.getUnsynced(limit)

    suspend fun markSynced(dates: List<String>, remoteId: String? = null) {
        if (dates.isNotEmpty()) dao.markSynced(dates, remoteId)
    }

    suspend fun deleteByDates(dates: List<String>) {
        if (dates.isNotEmpty()) dao.deleteByDates(dates)
    }

    private fun mergeSleep(old: SleepSessionEntity?, incoming: SleepSessionEntity): SleepSessionEntity {
        if (old == null) return incoming
        val incomingHasTs = incoming.sleepStartTimeMs != null || incoming.sleepEndTimeMs != null
        val oldHasTs = old.sleepStartTimeMs != null || old.sleepEndTimeMs != null
        val now = System.currentTimeMillis()
        val syncState = when {
            incomingHasTs && !oldHasTs -> SyncState.PENDING.name
            else -> old.sync.syncState
        }
        return SleepSessionEntity(
            date = incoming.date,
            sleepStartTimeMs = incoming.sleepStartTimeMs ?: old.sleepStartTimeMs,
            sleepEndTimeMs = incoming.sleepEndTimeMs ?: old.sleepEndTimeMs,
            sleepGoalMinutes = incoming.sleepGoalMinutes ?: old.sleepGoalMinutes,
            userSleepRating = incoming.userSleepRating ?: old.userSleepRating,
            batteryRanOut = incoming.batteryRanOut,
            sleepSkinTempCelsius = incoming.sleepSkinTempCelsius ?: old.sleepSkinTempCelsius,
            sleepSkinTempDeviation = incoming.sleepSkinTempDeviation ?: old.sleepSkinTempDeviation,
            sleepWakePhasesJson = incoming.sleepWakePhasesJson ?: old.sleepWakePhasesJson,
            sleepCyclesJson = incoming.sleepCyclesJson ?: old.sleepCyclesJson,
            sync = old.sync.copy(syncState = syncState, updatedAt = now)
        )
    }
}
