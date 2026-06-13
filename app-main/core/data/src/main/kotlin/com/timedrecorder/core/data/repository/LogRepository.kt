package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.AppLogEntry
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import kotlinx.coroutines.flow.Flow

/** 诊断日志 Repository 接口 */
interface LogRepository {
    fun observeRecentLogs(limit: Int = 100): Flow<List<AppLogEntry>>
    fun observeLogsByType(type: LogType, limit: Int = 50): Flow<List<AppLogEntry>>
    suspend fun log(type: LogType, level: LogLevel, content: String)
    suspend fun cleanupOldLogs(beforeMillis: Long)
}
