package com.owner.mindbody.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 通用高频样本写入缓冲区。
 * 与 [HrSampleBuffer] 相同策略：凑批或定时 flush，减少数据库小事务。
 */
class EntitySampleBuffer<T>(
    private val insertBatch: suspend (List<T>) -> Unit,
    private val maxBatchSize: Int = 30,
    private val flushIntervalMs: Long = 10_000L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = mutableListOf<T>()
    private val flushJob: Job

    init {
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    suspend fun enqueue(sample: T) {
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

    suspend fun enqueueAll(samples: List<T>) {
        if (samples.isEmpty()) return
        val batch = mutex.withLock {
            pending.addAll(samples)
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
        runBlocking { flush() }
        scope.coroutineContext[Job]?.cancel()
    }

    private fun <E> MutableList<E>.drain(): List<E> {
        if (isEmpty()) return emptyList()
        val copy = toList()
        clear()
        return copy
    }
}
