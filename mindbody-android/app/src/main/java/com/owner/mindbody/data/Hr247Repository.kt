package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.Hr247SampleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class Hr247Repository(private val database: AppDatabase) {

    private val dao = database.hr247SampleDao()
    private val zoneId = ZoneId.systemDefault()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<Hr247SampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveAll(samples: List<Hr247SampleEntity>) {
        buffer.enqueueAll(samples)
    }

    suspend fun flush() {
        buffer.flush()
    }

    suspend fun getUnsynced(limit: Int): List<Hr247SampleEntity> {
        flush()
        return dao.getUnsynced(limit)
    }

    suspend fun markSynced(ids: List<Long>, remoteId: String? = null) {
        dao.markSynced(ids, remoteId)
    }

    suspend fun markFailed(ids: List<Long>) {
        dao.markFailed(ids)
    }

    /** 观察今日 24/7 离线心率样本（断联期间由手表记录、重连后同步）。 */
    fun observeTodaySamples(): Flow<List<Hr247SampleEntity>> {
        val (start, end) = todayRangeMs()
        return observeBetween(start, end)
    }

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<Hr247SampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    private fun todayRangeMs(): Pair<Long, Long> {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return start to end
    }
}
