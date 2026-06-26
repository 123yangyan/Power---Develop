package com.owner.mindbody.keepalive

/**
 * 健康 / BLE 追踪场景的保活常量。
 *
 * 主路径：[com.owner.mindbody.polar.HrStreamService] 前台服务 + 90s PPI 推流；
 * 兜底：[com.owner.mindbody.worker.PpiStreamWorker] WorkManager 15 分钟周期（Service 被杀时）。
 */
object KeepAliveConfig {
    /** 前台服务内心跳日志间隔。 */
    const val HEARTBEAT_INTERVAL_MS = 60_000L

    /** 重启保护：统计窗口。 */
    const val RESTART_WINDOW_MS = 60_000L

    /** 重启保护：窗口内最多启动次数。 */
    const val MAX_STARTS_IN_WINDOW = 10

    /** 重启保护：触发冷却后的暂停时长。 */
    const val RESTART_COOLDOWN_MS = 5 * 60_000L

    /** UI 判定心跳过期的阈值（略大于心跳间隔）。 */
    const val HEARTBEAT_STALE_MS = 90_000L
}
