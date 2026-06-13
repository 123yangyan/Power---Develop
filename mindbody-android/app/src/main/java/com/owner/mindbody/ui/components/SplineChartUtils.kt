package com.owner.mindbody.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.owner.mindbody.data.local.HrSampleEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

data class ChartPoint(
    val minutesOfDay: Int,
    val bpm: Int
)

object SplineChartUtils {
    const val MIN_BPM = 40f
    const val MAX_BPM = 130f
    const val MINUTES_PER_DAY = 1440
    private const val DOWNSAMPLE_BUCKET_MINUTES = 5

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

    fun bpmToY(bpm: Float, height: Float, bottomPadding: Float = 20f, topPadding: Float = 25f): Float {
        val range = (MAX_BPM - MIN_BPM).coerceAtLeast(1f)
        val usable = height - bottomPadding - topPadding
        return topPadding + usable * (1f - (bpm - MIN_BPM) / range)
    }

    fun minutesToX(minutes: Int, width: Float): Float {
        return (minutes.toFloat() / MINUTES_PER_DAY) * width
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
}
