package com.owner.mindbody.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.owner.mindbody.data.local.HrSampleEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ChartPoint(
    val minutesOfDay: Int,
    val bpm: Int
)

data class ChartTimeWindow(
    val startMinutes: Int,
    val endMinutes: Int
) {
    val spanMinutes: Int get() = (endMinutes - startMinutes).coerceAtLeast(1)
}

data class BpmRange(
    val min: Float,
    val max: Float
) {
    val span: Float get() = (max - min).coerceAtLeast(1f)
}

object SplineChartUtils {
    const val MIN_BPM = 40f
    const val MAX_BPM = 130f
    const val MINUTES_PER_DAY = 1440
    private const val DOWNSAMPLE_BUCKET_MINUTES = 1
    private const val WINDOW_PADDING_MINUTES = 15
    private const val FALLBACK_HALF_WINDOW_MINUTES = 30

    fun downsampleByTime(samples: List<HrSampleEntity>, zoneId: ZoneId = ZoneId.systemDefault()): List<ChartPoint> {
        if (samples.isEmpty()) return emptyList()
        return samples
            .groupBy { sample ->
                val time = Instant.ofEpochMilli(sample.timestamp).atZone(zoneId).toLocalTime()
                time.hour * 60 + time.minute
            }
            .map { (minute, bucket) ->
                val bucketStart = (minute / DOWNSAMPLE_BUCKET_MINUTES) * DOWNSAMPLE_BUCKET_MINUTES
                ChartPoint(
                    minutesOfDay = bucketStart,
                    bpm = bucket.map { it.bpm }.average().roundToInt()
                )
            }
            .sortedBy { it.minutesOfDay }
            .distinctBy { it.minutesOfDay }
    }

    fun minutesOfDay(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime()
        return time.hour * 60 + time.minute
    }

    fun computeTimeWindow(
        points: List<ChartPoint>,
        paddingMinutes: Int = WINDOW_PADDING_MINUTES,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ChartTimeWindow {
        if (points.isEmpty()) {
            return fallbackWindow(minutesOfDay(System.currentTimeMillis(), zoneId))
        }

        val minMinute = points.minOf { it.minutesOfDay }
        val maxMinute = points.maxOf { it.minutesOfDay }

        if (points.size == 1 || minMinute == maxMinute) {
            return fallbackWindow(minMinute)
        }

        return ChartTimeWindow(
            startMinutes = (minMinute - paddingMinutes).coerceAtLeast(0),
            endMinutes = (maxMinute + paddingMinutes).coerceAtMost(MINUTES_PER_DAY)
        )
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

    fun minutesToX(minutes: Int, width: Float, window: ChartTimeWindow): Float {
        val relative = (minutes - window.startMinutes).toFloat() / window.spanMinutes
        return relative.coerceIn(0f, 1f) * width
    }

    fun formatTimeLabels(window: ChartTimeWindow, count: Int = 5): List<String> {
        if (count <= 1) {
            return listOf(formatMinute(window.startMinutes))
        }
        return (0 until count).map { index ->
            val minute = window.startMinutes + (window.spanMinutes * index / (count - 1))
            formatMinute(minute)
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

    private fun fallbackWindow(centerMinute: Int): ChartTimeWindow {
        return ChartTimeWindow(
            startMinutes = (centerMinute - FALLBACK_HALF_WINDOW_MINUTES).coerceAtLeast(0),
            endMinutes = (centerMinute + FALLBACK_HALF_WINDOW_MINUTES).coerceAtMost(MINUTES_PER_DAY)
        )
    }

    private fun formatMinute(minutes: Int): String {
        val clamped = minutes.coerceIn(0, MINUTES_PER_DAY)
        val hour = clamped / 60
        val minute = clamped % 60
        return "%02d:%02d".format(hour, minute)
    }
}
