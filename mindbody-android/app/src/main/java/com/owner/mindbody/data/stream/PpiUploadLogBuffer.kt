package com.owner.mindbody.data.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

/**
 * PPI 窗口上传尝试环形缓冲，供开发者诊断页订阅。
 * 始终采集（解锁开发者模式前也保留记录）。
 */
class PpiUploadLogBuffer {

    companion object {
        private const val MAX_ENTRIES = 200
    }

    private val deque = ArrayDeque<PpiWindowAttempt>(MAX_ENTRIES)
    private val lock = Any()
    private val nextId = AtomicLong(0L)

    private val _entries = MutableStateFlow<List<PpiWindowAttempt>>(emptyList())
    val entries: StateFlow<List<PpiWindowAttempt>> = _entries.asStateFlow()

    fun add(attempt: PpiWindowAttempt) {
        val snapshot: List<PpiWindowAttempt>
        synchronized(lock) {
            val stored = attempt.copy(id = nextId.incrementAndGet())
            if (deque.size >= MAX_ENTRIES) {
                deque.removeFirst()
            }
            deque.addLast(stored)
            snapshot = deque.toList()
        }
        _entries.value = snapshot
    }

    fun clear() {
        synchronized(lock) {
            deque.clear()
            _entries.value = emptyList()
        }
    }

    fun asJsonText(): String {
        synchronized(lock) {
            val array = JSONArray()
            deque.forEach { array.put(it.toJson()) }
            return array.toString(2)
        }
    }

    fun size(): Int = synchronized(lock) { deque.size }
}
