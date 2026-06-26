package com.owner.mindbody.keepalive

import com.owner.mindbody.polar.ConnectionState

/** 设备页「后台保活」卡片与心跳诊断用的快照。 */
data class KeepAliveStatus(
    val foregroundRunning: Boolean = false,
    val bleState: ConnectionState = ConnectionState.DISCONNECTED,
    val wakeLockHeld: Boolean = false,
    val batteryExempt: Boolean = false,
    val lastHeartbeatMs: Long = 0L,
    val heartbeatSeq: Int = 0,
    val lastStartReason: KeepAliveReason? = null,
    val lastStopReason: KeepAliveReason? = null,
) {
    val heartbeatFresh: Boolean
        get() = lastHeartbeatMs > 0L &&
            System.currentTimeMillis() - lastHeartbeatMs <= KeepAliveConfig.HEARTBEAT_STALE_MS
}
