package com.owner.mindbody.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 纯 Kotlin 实现的 on-device HRV 轻量指标计算。
 * 零依赖，运行在 PpiStreamWorker 推流前对 PPI 窗口做质量参考。
 *
 * 参考：Task Force HRV Standards (1996), HeartPy process_rr()
 */
object HrvOnDevice {
    /** 有效 RR 间期下限 (ms) */
    private const val RR_MIN = 300
    /** 有效 RR 间期上限 (ms) */
    private const val RR_MAX = 2000

    // ---------------------------------------------------------------
    // 公共 API
    // ---------------------------------------------------------------

    /** RMSSD — 相邻 RR 差值均方根，副交感活性的金标准。 */
    fun rmssd(rrListMs: List<Int>): Double {
        val diffs = successiveDiffs(rrListMs)
        if (diffs.isEmpty()) return Double.NaN
        val meanSquare = diffs.sumOf { it * it } / diffs.size.toDouble()
        return sqrt(meanSquare)
    }

    /** SDNN — 全部 RR 间期的标准差，反映整体心率变异性。 */
    fun sdnn(rrListMs: List<Int>): Double {
        val clean = filterValid(rrListMs)
        if (clean.size < 2) return Double.NaN
        val mean = clean.average()
        val variance = clean.sumOf { (it - mean).let { d -> d * d } } / (clean.size - 1).toDouble()
        return sqrt(variance)
    }

    /** pNN50 — 相邻 RR 差值 >50ms 的比例，副交感逐跳调节敏感指标。 */
    fun pnn50(rrListMs: List<Int>): Double {
        val diffs = successiveDiffs(rrListMs)
        if (diffs.isEmpty()) return Double.NaN
        val n50 = diffs.count { abs(it) > 50 }
        return n50.toDouble() / diffs.size
    }

    /** 平均心率 (bpm) — 从 RR 列表直接推算。 */
    fun meanBpm(rrListMs: List<Int>): Double {
        val clean = filterValid(rrListMs)
        if (clean.isEmpty()) return Double.NaN
        val meanRR = clean.average()
        return if (meanRR > 0) 60_000.0 / meanRR else Double.NaN
    }

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------

    private fun filterValid(rrListMs: List<Int>): List<Int> =
        rrListMs.filter { it in RR_MIN..RR_MAX }

    private fun successiveDiffs(rrListMs: List<Int>): List<Int> {
        val clean = filterValid(rrListMs)
        if (clean.size < 2) return emptyList()
        val diffs = mutableListOf<Int>()
        for (i in 1 until clean.size) {
            diffs.add(clean[i] - clean[i - 1])
        }
        return diffs
    }
}
