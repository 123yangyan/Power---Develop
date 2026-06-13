package com.timedrecorder.core.model

/**
 * T7：录音质量档位枚举。
 * @param bps            比特率（bits per second）
 * @param label          显示标签
 * @param estimateMbPerHour  每小时预估占用存储（MB）
 */
enum class AudioBitrate(
    val bps: Int,
    val label: String,
    val estimateMbPerHour: Float,
) {
    KBPS_32(32_000, "32 kbps", 14f),
    KBPS_64(64_000, "64 kbps", 28f),
    KBPS_128(128_000, "128 kbps", 56f),
}
