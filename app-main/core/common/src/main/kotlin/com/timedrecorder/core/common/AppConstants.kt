package com.timedrecorder.core.common

/** 全局常量 */
object AppConstants {
    /** 默认切片时长（分钟） */
    const val DEFAULT_SLICE_DURATION_MINUTES = 5

    /** 默认文件保留天数 */
    const val DEFAULT_RETENTION_DAYS = 7

    /** 默认轮询间隔（秒） */
    const val DEFAULT_POLL_INTERVAL_SECONDS = 30

    /** 默认最大轮询次数 */
    const val DEFAULT_POLL_MAX_ATTEMPTS = 10

    /** 单文件最大上传重试次数 */
    const val MAX_UPLOAD_RETRY_COUNT = 3

    /** deviceId 短前缀长度（用于文件命名） */
    const val DEVICE_ID_SHORT_LENGTH = 8
}
