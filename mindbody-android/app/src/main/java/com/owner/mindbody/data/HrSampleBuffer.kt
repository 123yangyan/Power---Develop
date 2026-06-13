package com.owner.mindbody.data

import com.owner.mindbody.data.local.HrSampleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 高频心率写入缓冲区。
 *
 * 为什么需要它：
 * 常连接模式下心率可能每秒写一次。如果每条都单独开启一次数据库事务，
 * 长时间运行会产生很多小事务。这里先把数据放入内存小队列，再批量写入 Room。
 */
class HrSampleBuffer(
    private val insertBatch: suspend (List<HrSampleEntity>) -> Unit,
    private val maxBatchSize: Int = 30,
    private val flushIntervalMs: Long = 10_000L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = mutableListOf<HrSampleEntity>()
    private val flushJob: Job

    init {
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    suspend fun enqueue(sample: HrSampleEntity) {
        val batch = mutex.withLock {
            pending.add(sample)
            if (pending.size >= maxBatchSize) {
                pending.drain()
            } else {
                emptyList()
            }
        }
        if (batch.isNotEmpty()) {
            insertBatch(batch)
        }
    }

    suspend fun flush() {
        val batch = mutex.withLock { pending.drain() }
        if (batch.isNotEmpty()) {
            insertBatch(batch)
        }
    }

    fun shutdown() {
        flushJob.cancel()
        scope.launch {
            flush()
        }
    }

    private fun MutableList<HrSampleEntity>.drain(): List<HrSampleEntity> {
        if (isEmpty()) return emptyList()
        val copy = toList()
        clear()
        return copy
    }
}
