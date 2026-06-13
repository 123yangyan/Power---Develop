package com.owner.mindbody.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 进程内环形日志缓冲，供开发者日志页订阅。
 * 始终采集（解锁前也保留启动日志）。
 */
object AppLogBuffer {

    private const val MAX_ENTRIES = 800

    private val deque = ArrayDeque<AppLogEntry>(MAX_ENTRIES)
    private val lock = Any()
    private val nextId = AtomicLong(0L)

    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    fun append(entry: AppLogEntry) {
        synchronized(lock) {
            val stored = entry.copy(id = nextId.incrementAndGet())
            if (deque.size >= MAX_ENTRIES) {
                deque.removeFirst()
            }
            deque.addLast(stored)
            _entries.value = deque.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            deque.clear()
            _entries.value = emptyList()
        }
    }

    /** 将全部日志合并为纯文本，供「复制全部」使用。 */
    fun asPlainText(): String {
        synchronized(lock) {
            return deque.joinToString(separator = "\n") { it.formatLine() }
        }
    }

    fun size(): Int = synchronized(lock) { deque.size }
}
