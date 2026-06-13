package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.HrSampleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

data class DailyHrStats(
    val count: Int = 0,
    val average: Int? = null,
    val min: Int? = null,
    val max: Int? = null
)

class HrRepository(private val database: AppDatabase) {

    private val dao = database.hrSampleDao()
    private val zoneId = ZoneId.systemDefault()
    private val buffer = HrSampleBuffer(
        insertBatch = { samples: List<HrSampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveSample(timestamp: Long, bpm: Int, rrMs: Int?) {
        // 原始心率样本是宝贵资料：这里仅写入，不再自动删除历史数据。
        buffer.enqueue(HrSampleEntity(timestamp = timestamp, bpm = bpm, rrMs = rrMs))
    }

    suspend fun flush() {
        buffer.flush()
    }

    fun observeTodaySamples(): Flow<List<HrSampleEntity>> {
        val (start, end) = todayRangeMs()
        return dao.observeBetween(start, end)
    }

    suspend fun getSamplesPage(limit: Int, offset: Int): List<HrSampleEntity> {
        return dao.getPage(limit, offset)
    }

    suspend fun getTodayStats(): DailyHrStats {
        flush()
        val (start, end) = todayRangeMs()
        val count = dao.countBetween(start, end)
        if (count == 0) return DailyHrStats()
        return DailyHrStats(
            count = count,
            average = dao.averageBetween(start, end)?.toInt(),
            min = dao.minBetween(start, end),
            max = dao.maxBetween(start, end)
        )
    }

    suspend fun getHrNearTimestamp(timestamp: Long, windowMs: Long = 5 * 60 * 1000L): Int? {
        flush()
        val samples = dao.getBetween(timestamp - windowMs, timestamp + windowMs)
        if (samples.isEmpty()) return null
        return samples.map { it.bpm }.average().toInt()
    }

    suspend fun getUnsynced(limit: Int): List<HrSampleEntity> {
        flush()
        return dao.getUnsynced(limit)
    }

    suspend fun markSynced(ids: List<Long>, remoteId: String? = null) {
        dao.markSynced(ids, remoteId)
    }

    suspend fun markFailed(ids: List<Long>) {
        dao.markFailed(ids)
    }

    private fun todayRangeMs(): Pair<Long, Long> {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return start to end
    }
}
