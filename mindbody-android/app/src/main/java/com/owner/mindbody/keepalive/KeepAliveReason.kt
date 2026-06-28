package com.owner.mindbody.keepalive

/** 前台服务启停原因，便于运行日志追溯。 */
enum class KeepAliveReason {
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED,
    RECONNECTING,
    HEART_RATE_SCREEN,
    USER_DISCONNECT,
    BLE_OFF,
    CHECK_RECOVER,
}
