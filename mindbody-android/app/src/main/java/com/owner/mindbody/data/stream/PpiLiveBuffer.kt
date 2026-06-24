package com.owner.mindbody.data.stream

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * PPI 在线流环形缓冲区 — 线程安全，支持多生产者（PolarBleManager 写入）
 * 和单消费者（PpiStreamWorker 取窗推流）。
 *
 * 容量 600 样本 ≈ 平均 7.5 分钟数据（假设平均 HR 80bpm），
 * 足以覆盖 60s—300s 的分析窗口。
 */
class PpiLiveBuffer(val maxSize: Int = 600) {

    companion object {
        /** 与 [com.owner.mindbody.data.HrvOnDevice] RR_MIN 对齐（HR ≤ 200 bpm）。 */
        private const val RR_MIN_MS = 300
        private const val RR_MAX_MS = 2_000
        /**
         * Polar errorEstimate 过滤阈值。
         * Polar 官方：< 10ms 非常准，> 30ms 可疑，> 50ms 大概率伪影。
         * 设为 50ms 可多保留信号边缘但仍可信的样本，减少 coverage 不足导致的跳包。
         */
        private const val ERROR_ESTIMATE_MAX_MS = 50
    }

    /** 缓冲区单条样本。 */
    data class Sample(
        val timestampMs: Long,
        val ppiMs: Int,
        val hrBpm: Int,
        val blocker: Boolean,
        val skinContactOk: Boolean,
        val errorEstimateMs: Int = 0,
        val accMagnitudeMg: Int? = null
    )

    /** [drainWindowAtomic] 返回的全量 + 清洗后样本。 */
    data class DrainResult(
        val allSamples: List<Sample>,
        val cleanRrMs: List<Int>,
    )

    // ---------------------------------------------------------------
    // 内部状态
    // ---------------------------------------------------------------
    private val lock = ReentrantLock()
    private val buffer = ArrayList<Sample>(maxSize)

    // ---------------------------------------------------------------
    // 写入（PolarBleManager 调用，高频 but 轻量）
    // ---------------------------------------------------------------

    fun push(
        timestampMs: Long,
        ppiMs: Int,
        hrBpm: Int,
        blocker: Boolean,
        skinContactOk: Boolean,
        errorEstimateMs: Int = 0,
        accMagnitudeMg: Int? = null
    ) {
        lock.withLock {
            if (buffer.size >= maxSize) {
                // 循环覆盖：丢掉最旧的 1/3
                val removeCount = maxSize / 3
                buffer.subList(0, removeCount).clear()
            }
            buffer.add(
                Sample(
                    timestampMs = timestampMs,
                    ppiMs = ppiMs,
                    hrBpm = hrBpm,
                    blocker = blocker,
                    skinContactOk = skinContactOk,
                    errorEstimateMs = errorEstimateMs,
                    accMagnitudeMg = accMagnitudeMg
                )
            )
        }
    }

    // ---------------------------------------------------------------
    // 读取（PpiStreamWorker 调用）
    // ---------------------------------------------------------------

    /**
     * 原子读取 [sinceMs] 起的窗口：全量样本 + 清洗后 RR 列表，单次加锁避免并发 push 导致 coverage 偏差。
     * 返回副本，不影响缓冲区。
     */
    fun drainWindowAtomic(sinceMs: Long): DrainResult {
        lock.withLock {
            val all = buffer
                .filter { s -> s.timestampMs >= sinceMs }
                .sortedBy { it.timestampMs }
            val clean = all
                .filter { s ->
                    !s.blocker &&
                        s.skinContactOk &&
                        s.errorEstimateMs <= ERROR_ESTIMATE_MAX_MS &&
                        s.ppiMs in RR_MIN_MS..RR_MAX_MS
                }
                .map { it.ppiMs }
            return DrainResult(allSamples = all, cleanRrMs = clean)
        }
    }

    /**
     * 取 [sinceMs] 之后的有效 PPI 样本（非 blocker、skinContactOk、errorEstimate 合格），
     * 返回副本，不影响缓冲区。
     *
     * @param sinceMs Unix 毫秒 — 只取时间戳 >= 此值的样本
     * @return 按时间排序的有效 PPI 毫秒值列表
     */
    fun drainWindow(sinceMs: Long): List<Int> = drainWindowAtomic(sinceMs).cleanRrMs

    /** 取指定窗口内的完整 Sample（含元数据），用于推流 payload 构建。 */
    fun drainSamples(sinceMs: Long): List<Sample> = drainWindowAtomic(sinceMs).allSamples

    /** 缓冲区中最新的时间戳（用于计算 next window start）。 */
    fun lastTs(): Long = lock.withLock { buffer.lastOrNull()?.timestampMs ?: 0L }

    /** 当前缓冲区内样本数（调试用）。 */
    fun size(): Int = lock.withLock { buffer.size }
}
