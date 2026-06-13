package com.timedrecorder.core.model

/**
 * 应用诊断日志，对应 PRD §13.5 app_log。
 */
data class AppLogEntry(
    val id: Long = 0,
    val logType: LogType,
    val logLevel: LogLevel,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
