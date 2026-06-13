package com.timedrecorder.sync.worker

import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 启动时补偿轮询：对已上传但未拿到云端处理结果的文件重新入队 PollResultWorker。
 */
@Singleton
class PendingPollResumer @Inject constructor(
    private val audioFileRepository: AudioFileRepository,
    private val workScheduler: WorkScheduler,
    private val preferencesDataSource: PreferencesDataSource,
    private val logRepository: LogRepository,
) {
    suspend fun resume() {
        val prefs = preferencesDataSource.userPreferences.first()
        val pendingFiles = audioFileRepository.getUploadedAwaitingResult()
        if (pendingFiles.isEmpty()) return

        logRepository.log(
            LogType.NETWORK,
            LogLevel.INFO,
            "启动补偿轮询: ${pendingFiles.size} 个已上传文件待拉取结果",
        )

        for (file in pendingFiles) {
            val serverFileId = file.serverFileId ?: continue
            workScheduler.enqueuePollWithOptions(
                serverFileId = serverFileId,
                localFileId = file.id,
                maxAttempts = prefs.pollMaxAttempts,
                intervalSeconds = prefs.pollIntervalSeconds,
            )
            logRepository.log(
                LogType.NETWORK,
                LogLevel.INFO,
                "补偿轮询已入队: ${file.fileName} -> $serverFileId",
            )
        }
    }
}
