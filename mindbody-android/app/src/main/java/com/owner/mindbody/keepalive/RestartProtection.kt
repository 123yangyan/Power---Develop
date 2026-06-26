package com.owner.mindbody.keepalive

import com.owner.mindbody.util.AppLogger
import java.util.ArrayDeque

/**
 * 滑动窗口重启保护：60 秒内超过 [KeepAliveConfig.MAX_STARTS_IN_WINDOW] 次启动则冷却 5 分钟。
 */
internal object RestartProtection {

    private const val TAG = "KeepAlive"

    private val startTimestamps = ArrayDeque<Long>()
    private var cooldownUntilMs = 0L

    fun allowStart(): Boolean {
        val now = System.currentTimeMillis()
        if (now < cooldownUntilMs) {
            AppLogger.w(
                TAG,
                "restart blocked: cooldown ${(cooldownUntilMs - now) / 1000}s remaining"
            )
            return false
        }
        while (startTimestamps.isNotEmpty() &&
            now - startTimestamps.first() > KeepAliveConfig.RESTART_WINDOW_MS
        ) {
            startTimestamps.removeFirst()
        }
        if (startTimestamps.size >= KeepAliveConfig.MAX_STARTS_IN_WINDOW) {
            cooldownUntilMs = now + KeepAliveConfig.RESTART_COOLDOWN_MS
            AppLogger.w(
                TAG,
                "restart blocked: ${startTimestamps.size} starts in " +
                    "${KeepAliveConfig.RESTART_WINDOW_MS / 1000}s, " +
                    "cooldown ${KeepAliveConfig.RESTART_COOLDOWN_MS / 1000}s"
            )
            startTimestamps.clear()
            return false
        }
        startTimestamps.addLast(now)
        return true
    }
}
