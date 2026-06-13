package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.database.dao.AppLogDao
import com.timedrecorder.core.database.entity.AppLogEntity
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.AppLogEntry
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineLogRepository @Inject constructor(
    private val appLogDao: AppLogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LogRepository {

    override fun observeRecentLogs(limit: Int): Flow<List<AppLogEntry>> =
        appLogDao.observeRecent(limit).map { list -> list.map { it.asExternalModel() } }

    override fun observeLogsByType(type: LogType, limit: Int): Flow<List<AppLogEntry>> =
        appLogDao.observeByType(type, limit).map { list -> list.map { it.asExternalModel() } }

    override suspend fun log(type: LogType, level: LogLevel, content: String) {
        withContext(ioDispatcher) {
            appLogDao.insert(
                AppLogEntity(
                    logType = type,
                    logLevel = level,
                    content = content,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun cleanupOldLogs(beforeMillis: Long) = withContext(ioDispatcher) {
        appLogDao.deleteOlderThan(beforeMillis)
    }
}
