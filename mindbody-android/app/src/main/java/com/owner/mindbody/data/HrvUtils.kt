package com.owner.mindbody.data

import kotlin.math.sqrt

data class RrIntervalSample(
    val timestampMs: Long,
    val rrMs: Int
)

data class RmssdPoint(
    val timestampMs: Long,
    val rmssdMs: Float
)

object HrvUtils {
    private const val DEFAULT_WINDOW_MS = 60_000L
    private const val DEFAULT_STEP_MS = 30_000L
    private const val MIN_INTERVAL_COUNT = 4

    fun computeRmssd(
        intervals: List<RrIntervalSample>,
        windowMs: Long = DEFAULT_WINDOW_MS,
        stepMs: Long = DEFAULT_STEP_MS,
        minIntervalCount: Int = MIN_INTERVAL_COUNT
    ): List<RmssdPoint> {
        if (windowMs <= 0L || stepMs <= 0L || minIntervalCount < 2) return emptyList()

        val sorted = intervals
            .filter { it.rrMs in 300..2_000 }
            .sortedBy { it.timestampMs }
        if (sorted.size < minIntervalCount) return emptyList()

        val diffCount = sorted.size - 1
        val diffPrevTimes = LongArray(diffCount)
        val diffCurrentTimes = LongArray(diffCount)
        val diffSquares = DoubleArray(diffCount)

        for (index in 1 until sorted.size) {
            val diffIndex = index - 1
            val previous = sorted[index - 1]
            val current = sorted[index]
            val diff = (current.rrMs - previous.rrMs).toDouble()
            diffPrevTimes[diffIndex] = previous.timestampMs
            diffCurrentTimes[diffIndex] = current.timestampMs
            diffSquares[diffIndex] = diff * diff
        }

        val start = sorted.first().timestampMs
        val end = sorted.last().timestampMs
        val result = mutableListOf<RmssdPoint>()
        var windowStart = start
        var addIndex = 0
        var removeIndex = 0
        var squaredSum = 0.0
        var includedDiffCount = 0
        val included = BooleanArray(diffCount)
        val minDiffCount = minIntervalCount - 1

        while (windowStart <= end) {
            val windowEnd = windowStart + windowMs

            while (addIndex < diffCount && diffCurrentTimes[addIndex] <= windowEnd) {
                if (diffPrevTimes[addIndex] >= windowStart) {
                    included[addIndex] = true
                    squaredSum += diffSquares[addIndex]
                    includedDiffCount += 1
                }
                addIndex += 1
            }

            while (removeIndex < diffCount && diffPrevTimes[removeIndex] < windowStart) {
                if (included[removeIndex]) {
                    included[removeIndex] = false
                    squaredSum -= diffSquares[removeIndex]
                    includedDiffCount -= 1
                }
                removeIndex += 1
            }

            if (includedDiffCount >= minDiffCount) {
                result += RmssdPoint(
                    timestampMs = windowStart + windowMs / 2,
                    rmssdMs = sqrt(squaredSum / includedDiffCount).toFloat()
                )
            }

            windowStart += stepMs
        }

        return result
    }
}
