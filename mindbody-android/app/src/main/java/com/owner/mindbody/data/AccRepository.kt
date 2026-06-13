package com.owner.mindbody.data

import com.owner.mindbody.data.local.AccMinuteSummaryEntity
import com.owner.mindbody.data.local.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/**
 * 加速度仓库：内存中按分钟聚合，落库时只保存每分钟均值/峰值幅度。
 */
class AccRepository(private val database: AppDatabase) {

    private val dao = database.accMinuteSummaryDao()
    private val aggregator = AccMinuteAggregator()
    private val flushMutex = Mutex()

    suspend fun ingestSample(x: Int, y: Int, z: Int, timestampMs: Long = System.currentTimeMillis()) {
        val ready = aggregator.addSample(x, y, z, timestampMs)
        if (ready != null) {
            dao.upsertAll(listOf(ready))
        }
    }

    suspend fun ingestBatch(samples: List<Triple<Int, Int, Int>>, timestampMs: Long = System.currentTimeMillis()) {
        samples.forEach { (x, y, z) ->
            ingestSample(x, y, z, timestampMs)
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            aggregator.flushPending()?.let { dao.upsertAll(listOf(it)) }
        }
    }
}

/**
 * 将高频 ACC 样本按「分钟起始时间戳」分桶聚合。
 */
internal class AccMinuteAggregator {

    private val mutex = Mutex()
    private var currentMinuteStart: Long? = null
    private var magnitudeSum = 0.0
    private var maxMagnitude = 0f
    private var sampleCount = 0

    suspend fun addSample(x: Int, y: Int, z: Int, timestampMs: Long): AccMinuteSummaryEntity? {
        val minuteStart = timestampMs - (timestampMs % MINUTE_MS)
        return mutex.withLock {
            if (currentMinuteStart != null && minuteStart > currentMinuteStart!!) {
                val completed = buildEntity(currentMinuteStart!!)
                resetBucket(minuteStart, x, y, z)
                completed
            } else {
                if (currentMinuteStart == null) {
                    currentMinuteStart = minuteStart
                }
                accumulate(x, y, z)
                null
            }
        }
    }

    suspend fun flushPending(): AccMinuteSummaryEntity? {
        return mutex.withLock {
            currentMinuteStart?.let { start ->
                val entity = buildEntity(start)
                currentMinuteStart = null
                magnitudeSum = 0.0
                maxMagnitude = 0f
                sampleCount = 0
                entity
            }
        }
    }

    private fun resetBucket(minuteStart: Long, x: Int, y: Int, z: Int) {
        currentMinuteStart = minuteStart
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

    private fun buildEntity(minuteStart: Long): AccMinuteSummaryEntity {
        val avg = if (sampleCount > 0) (magnitudeSum / sampleCount).toFloat() else 0f
        return AccMinuteSummaryEntity(
            minuteTimestamp = minuteStart,
            avgMagnitudeMg = avg,
            maxMagnitudeMg = maxMagnitude,
            sampleCount = sampleCount
        )
    }

    companion object {
        private const val MINUTE_MS = 60_000L
    }
}
