package com.owner.mindbody.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.owner.mindbody.data.local.HrSampleEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ChartPoint(
    val timestampMs: Long,
    val bpm: Int
)

data class ChartValuePoint(
    val timestampMs: Long,
    val value: Float
)

data class ChartTimeWindow(
    val startMs: Long,
    val endMs: Long
) {
    val spanMs: Long get() = (endMs - startMs).coerceAtLeast(1)
}

enum class ChartWindowPreset(
    val label: String,
    val durationMs: Long,
    val bucketMs: Long
) {
    ONE_HOUR("1h", 3_600_000L, 10_000L),
    SIX_HOURS("6h", 6 * 3_600_000L, 60_000L),
    DAY("24h", 24 * 3_600_000L, 120_000L)
}

data class BpmRange(
    val min: Float,
    val max: Float
) {
    val span: Float get() = (max - min).coerceAtLeast(1f)
}

data class ChartValueRange(
    val min: Float,
    val max: Float
) {
    val span: Float get() = (max - min).coerceAtLeast(1f)
}

data class ChartExerciseBand(
    val startMs: Long,
    val endMs: Long,
    val label: String
)

data class MindBodyChartState(
    val hrPoints: List<ChartValuePoint> = emptyList(),
    val tempPoints: List<ChartValuePoint> = emptyList(),
    val hrvPoints: List<ChartValuePoint> = emptyList(),
    val activityPoints: List<ChartValuePoint> = emptyList(),
    val exerciseBands: List<ChartExerciseBand> = emptyList(),
    val window: ChartTimeWindow,
    val preset: ChartWindowPreset,
    val todayStartMs: Long,
    val nowMs: Long
)

object SplineChartUtils {
    const val MIN_BPM = 40f
    const val MAX_BPM = 130f
    /** 降采样桶：每 10 秒聚合一个点，提升曲线平滑度与实时推进感 */
    private const val DOWNSAMPLE_BUCKET_SECONDS = 10
    /** 图表 X 轴固定视窗：最近 1 小时 */
    const val WINDOW_DURATION_MS = 3_600_000L
    private const val FALLBACK_HALF_WINDOW_MS = 30 * 60_000L

    private val timeLabelFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun downsampleByTime(
        samples: List<HrSampleEntity>,
        bucketSeconds: Int = DOWNSAMPLE_BUCKET_SECONDS
    ): List<ChartPoint> {
        if (samples.isEmpty()) return emptyList()
        val bucketMs = bucketSeconds * 1000L
        return samples
            .groupBy { sample ->
                (sample.timestamp / bucketMs) * bucketMs
            }
            .map { (bucketStart, bucket) ->
                ChartPoint(
                    timestampMs = bucketStart,
                    bpm = bucket.map { it.bpm }.average().roundToInt()
                )
            }
            .sortedBy { it.timestampMs }
    }

    fun downsampleValues(
        points: List<ChartValuePoint>,
        bucketMs: Long
    ): List<ChartValuePoint> {
        if (points.isEmpty()) return emptyList()
        return points
            .groupBy { point -> (point.timestampMs / bucketMs) * bucketMs }
            .map { (bucketStart, bucket) ->
                ChartValuePoint(
                    timestampMs = bucketStart,
                    value = bucket.map { it.value }.average().toFloat()
                )
            }
            .sortedBy { it.timestampMs }
    }

    /**
     * 固定最近 1 小时视窗：以最新样本（或当前时间）为右边界，向左推 3600 秒。
     * 仅保留视窗内的点，避免全天 min/max 导致跨度被拉长。
     */
    fun computeTimeWindow(
        points: List<ChartPoint>,
        windowDurationMs: Long = WINDOW_DURATION_MS,
        nowMs: Long = System.currentTimeMillis()
    ): ChartTimeWindow {
        val endMs = points.maxOfOrNull { it.timestampMs } ?: nowMs
        val startMs = endMs - windowDurationMs
        return ChartTimeWindow(startMs = startMs, endMs = endMs)
    }

    fun clampTimeWindow(
        requestedStartMs: Long,
        durationMs: Long,
        minStartMs: Long,
        maxEndMs: Long
    ): ChartTimeWindow {
        val clampedDuration = durationMs.coerceAtMost((maxEndMs - minStartMs).coerceAtLeast(1L))
        val latestStart = (maxEndMs - clampedDuration).coerceAtLeast(minStartMs)
        val start = requestedStartMs.coerceIn(minStartMs, latestStart)
        return ChartTimeWindow(startMs = start, endMs = start + clampedDuration)
    }

    /** 过滤出落在视窗内的数据点 */
    fun filterPointsInWindow(
        points: List<ChartPoint>,
        window: ChartTimeWindow
    ): List<ChartPoint> {
        return points.filter { it.timestampMs in window.startMs..window.endMs }
    }

    fun filterValuePointsInWindow(
        points: List<ChartValuePoint>,
        window: ChartTimeWindow
    ): List<ChartValuePoint> {
        return points.filter { it.timestampMs in window.startMs..window.endMs }
    }

    fun computeBpmRange(points: List<ChartPoint>, restingBpm: Int): BpmRange {
        if (points.isEmpty()) {
            return BpmRange(MIN_BPM, MAX_BPM)
        }

        val dataMin = points.minOf { it.bpm }.toFloat()
        val dataMax = points.maxOf { it.bpm }.toFloat()
        val resting = restingBpm.toFloat()

        var rangeMin = min(dataMin - 5f, resting - 5f)
        var rangeMax = max(dataMax + 5f, resting + 5f)

        rangeMin = rangeMin.coerceIn(MIN_BPM, MAX_BPM - 20f)
        rangeMax = rangeMax.coerceIn(rangeMin + 20f, MAX_BPM)

        return BpmRange(min = rangeMin, max = rangeMax)
    }

    fun computeValueBpmRange(points: List<ChartValuePoint>, restingBpm: Int): BpmRange {
        if (points.isEmpty()) {
            return BpmRange(MIN_BPM, MAX_BPM)
        }

        val dataMin = points.minOf { it.value }
        val dataMax = points.maxOf { it.value }
        val resting = restingBpm.toFloat()

        var rangeMin = min(dataMin - 5f, resting - 5f)
        var rangeMax = max(dataMax + 5f, resting + 5f)

        rangeMin = rangeMin.coerceIn(MIN_BPM, MAX_BPM - 20f)
        rangeMax = rangeMax.coerceIn(rangeMin + 20f, MAX_BPM)

        return BpmRange(min = rangeMin, max = rangeMax)
    }

    fun computeValueRange(
        points: List<ChartValuePoint>,
        fallbackMin: Float,
        fallbackMax: Float,
        padding: Float
    ): ChartValueRange {
        if (points.isEmpty()) return ChartValueRange(fallbackMin, fallbackMax)
        val minValue = points.minOf { it.value } - padding
        val maxValue = points.maxOf { it.value } + padding
        if (maxValue <= minValue) {
            return ChartValueRange(minValue - 1f, maxValue + 1f)
        }
        return ChartValueRange(minValue, maxValue)
    }

    fun bpmToY(
        bpm: Float,
        height: Float,
        range: BpmRange,
        bottomPadding: Float = 20f,
        topPadding: Float = 25f
    ): Float {
        val usable = height - bottomPadding - topPadding
        val normalized = ((bpm - range.min) / range.span).coerceIn(0f, 1f)
        return topPadding + usable * (1f - normalized)
    }

    fun valueToY(
        value: Float,
        height: Float,
        range: ChartValueRange,
        bottomPadding: Float = 20f,
        topPadding: Float = 25f
    ): Float {
        val usable = height - bottomPadding - topPadding
        val normalized = ((value - range.min) / range.span).coerceIn(0f, 1f)
        return topPadding + usable * (1f - normalized)
    }

    fun normalizedToY(
        value: Float,
        height: Float,
        range: ChartValueRange,
        bottomPadding: Float = 20f,
        topPadding: Float = 25f
    ): Float {
        return valueToY(value, height, range, bottomPadding, topPadding)
    }

    fun timestampToX(timestampMs: Long, width: Float, window: ChartTimeWindow): Float {
        val relative = (timestampMs - window.startMs).toFloat() / window.spanMs
        return relative.coerceIn(0f, 1f) * width
    }

    fun xToTimestamp(x: Float, width: Float, window: ChartTimeWindow): Long {
        val relative = (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return window.startMs + (window.spanMs * relative).toLong()
    }

    fun findNearestPoint(
        points: List<ChartValuePoint>,
        timestampMs: Long
    ): ChartValuePoint? {
        return points.minByOrNull { kotlin.math.abs(it.timestampMs - timestampMs) }
    }

    /** 1 小时视窗：5 个刻度，约每 15 分钟一个标签 */
    fun formatTimeLabels(
        window: ChartTimeWindow,
        count: Int = 5,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        if (count <= 1) {
            return listOf(formatTimestamp(window.startMs, zoneId))
        }
        return (0 until count).map { index ->
            val timestampMs = window.startMs + (window.spanMs * index / (count - 1))
            formatTimestamp(timestampMs, zoneId)
        }
    }

    /** 移植 HTML solveSplinePath：用 cubic 控制点平滑连接各数据点 */
    fun buildSplinePath(
        points: List<Offset>,
        closeBottom: Boolean = false,
        height: Float = 170f
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            return path
        }

        path.moveTo(points[0].x, points[0].y)
        val tension = 0.35f
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cpX1 = p0.x + (p1.x - p0.x) * tension
            val cpY1 = p0.y
            val cpX2 = p1.x - (p1.x - p0.x) * tension
            val cpY2 = p1.y
            path.cubicTo(cpX1, cpY1, cpX2, cpY2, p1.x, p1.y)
        }

        if (closeBottom) {
            path.lineTo(points.last().x, height - 15f)
            path.lineTo(points.first().x, height - 15f)
            path.close()
        }
        return path
    }

    fun fallbackWindow(centerMs: Long): ChartTimeWindow {
        return ChartTimeWindow(
            startMs = centerMs - FALLBACK_HALF_WINDOW_MS,
            endMs = centerMs + FALLBACK_HALF_WINDOW_MS
        )
    }

    private fun formatTimestamp(timestampMs: Long, zoneId: ZoneId): String {
        val time = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalTime()
        return time.format(timeLabelFormatter)
    }
}
