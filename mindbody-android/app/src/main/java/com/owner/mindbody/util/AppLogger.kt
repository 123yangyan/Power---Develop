package com.owner.mindbody.util

import android.util.Log

/**
 * 统一日志入口：同时写入 Logcat 与 AppLogBuffer。
 */
object AppLogger {

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        AppLogBuffer.append(
            AppLogEntry(
                id = 0L,
                timestampMs = System.currentTimeMillis(),
                level = LogLevel.DEBUG,
                tag = tag,
                message = message
            )
        )
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        AppLogBuffer.append(
            AppLogEntry(
                id = 0L,
                timestampMs = System.currentTimeMillis(),
                level = LogLevel.INFO,
                tag = tag,
                message = message
            )
        )
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        appendWarn(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        appendWarn(tag, "$message (${throwable.message})")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        val fullMessage = if (throwable != null) {
            "$message (${throwable.message})"
        } else {
            message
        }
        AppLogBuffer.append(
            AppLogEntry(
                id = 0L,
                timestampMs = System.currentTimeMillis(),
                level = LogLevel.ERROR,
                tag = tag,
                message = fullMessage
            )
        )
    }

    private fun appendWarn(tag: String, message: String) {
        AppLogBuffer.append(
            AppLogEntry(
                id = 0L,
                timestampMs = System.currentTimeMillis(),
                level = LogLevel.WARN,
                tag = tag,
                message = message
            )
        )
    }
}
