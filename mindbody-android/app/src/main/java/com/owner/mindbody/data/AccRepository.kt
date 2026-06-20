package com.owner.mindbody.data

import com.owner.mindbody.data.local.AccMinuteSummaryEntity
import com.owner.mindbody.data.local.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/**
 * 加速度仓库：10 秒桶聚合摘要落库（acc_minute_summary 表，minuteTimestamp 为桶起始 ms）。
 */
class AccRepository(private val database: AppDatabase) {

    private val summaryDao = database.accMinuteSummaryDao()
    private val aggregator = AccMinuteAggregator()
    private val flushMutex = Mutex()

    suspend fun ingestSample(x: Int, y: Int, z: Int, timestampMs: Long = System.currentTimeMillis()) {
        val ready = aggregator.addSample(x, y, z, timestampMs)
        if (ready != null) {
            summaryDao.upsertAll(listOf(ready))
        }
    }

    suspend fun ingestBatch(samples: List<Triple<Int, Int, Int>>, timestampMs: Long = System.currentTimeMillis()) {
        samples.forEach { (x, y, z) ->
            ingestSample(x, y, z, timestampMs)
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            aggregator.flushPending()?.let { summaryDao.upsertAll(listOf(it)) }
        }
    }

    suspend fun getUnsynced(limit: Int): List<AccMinuteSummaryEntity> {
        flush()
        return summaryDao.getUnsynced(limit)
    }

    suspend fun markSynced(ids: List<Long>, remoteId: String? = null) {
        summaryDao.markSynced(ids, remoteId)
    }

    suspend fun markFailed(ids: List<Long>) {
        summaryDao.markFailed(ids)
    }
}

/**
 * 将高频 ACC 样本按「10 秒桶起始时间戳」分桶聚合。
 */
internal class AccMinuteAggregator {

    private val mutex = Mutex()
    private var currentBucketStart: Long? = null
    private var magnitudeSum = 0.0
    private var maxMagnitude = 0f
    private var sampleCount = 0

    suspend fun addSample(x: Int, y: Int, z: Int, timestampMs: Long): AccMinuteSummaryEntity? {
        val bucketStart = timestampMs - (timestampMs % BUCKET_MS)
        return mutex.withLock {
            val currentStart = currentBucketStart
            if (currentStart != null && bucketStart > currentStart) {
                val completed = buildEntity(currentStart)
                resetBucket(bucketStart, x, y, z)
                completed
            } else {
                if (currentBucketStart == null) {
                    currentBucketStart = bucketStart
                }
                accumulate(x, y, z)
                null
            }
        }
    }

    suspend fun flushPending(): AccMinuteSummaryEntity? {
        return mutex.withLock {
            currentBucketStart?.let { start ->
                val entity = buildEntity(start)
                currentBucketStart = null
                magnitudeSum = 0.0
                maxMagnitude = 0f
                sampleCount = 0
                entity
            }
        }
    }

    private fun resetBucket(bucketStart: Long, x: Int, y: Int, z: Int) {
        currentBucketStart = bucketStart
        magnitudeSum = 0.0
        maxMagnitude = 0f
        sampleCount = 0
        accumulate(x, y, z)
    }

    private fun accumulate(x: Int, y: Int, z: Int) {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        magnitudeSum += magnitude
        if (magnitude > maxMagnitude) maxMagnitude = magnitude
        sampleCount++
    }

    private fun buildEntity(bucketStart: Long): AccMinuteSummaryEntity {
        val avg = if (sampleCount > 0) (magnitudeSum / sampleCount).toFloat() else 0f
        return AccMinuteSummaryEntity(
            minuteTimestamp = bucketStart,
            avgMagnitudeMg = avg,
            maxMagnitudeMg = maxMagnitude,
            sampleCount = sampleCount
        )
    }

    companion object {
        private const val BUCKET_MS = 10_000L
    }
}
