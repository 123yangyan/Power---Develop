package com.owner.mindbody.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单条应用内日志级别。 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    fun label(): String = name
}

/**
 * 环形缓冲中的一条日志记录。
 * [id] 由 [AppLogBuffer] 在写入时分配，供 LazyColumn 唯一 key 使用。
 */
data class AppLogEntry(
    val id: Long = 0L,
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    /** 格式化为单行文本，供 UI 展示与复制。 */
    fun formatLine(): String {
        val time = TIME_FORMAT.format(Date(timestampMs))
        return "$time [${level.label()}] $tag: $message"
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }
}
