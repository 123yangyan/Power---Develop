package com.timedrecorder.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.UploadRepository
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 上传 Worker：处理单个或批量待上传文件。
 * 失败自动重试，超过 3 次标记失败。
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val uploadRepository: UploadRepository,
    private val audioFileRepository: AudioFileRepository,
    private val workScheduler: WorkScheduler,
    private val preferencesDataSource: PreferencesDataSource,
    private val logRepository: LogRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val processAll = inputData.getBoolean(KEY_PROCESS_ALL, false)
        val fileId = inputData.getLong(KEY_FILE_ID, -1L)
        val prefs = preferencesDataSource.userPreferences.first()

        val isSingleFileRetry = !processAll && fileId >= 0

        val filesToUpload = if (processAll) {
            audioFileRepository.observePendingUploads().first()
        } else if (isSingleFileRetry) {
            audioFileRepository.resetUploadForRetry(fileId)
            listOfNotNull(audioFileRepository.getFileById(fileId))
        } else {
            emptyList()
        }

        if (filesToUpload.isEmpty()) {
            logRepository.log(LogType.UPLOAD, LogLevel.WARN, "上传 Worker: 无待上传文件")
            return Result.success()
        }

        val startMsg = if (processAll) {
            "开始上传 Worker: 批量 ${filesToUpload.size} 个文件"
        } else {
            "开始上传 Worker: fileId=$fileId"
        }
        logRepository.log(LogType.UPLOAD, LogLevel.INFO, startMsg)

        var hasFailure = false
        for (file in filesToUpload) {
            if (file.uploadStatus == UploadStatus.SUCCESS) {
                logRepository.log(LogType.UPLOAD, LogLevel.INFO, "跳过已上传: ${file.fileName}")
                continue
            }
            val uploadResult = uploadRepository.uploadFile(file.id)
            uploadResult.onSuccess { serverFileId ->
                workScheduler.enqueuePollWithOptions(
                    serverFileId = serverFileId,
                    localFileId = file.id,
                    maxAttempts = prefs.pollMaxAttempts,
                    intervalSeconds = prefs.pollIntervalSeconds,
                )
            }.onFailure { error ->
                hasFailure = true
                logRepository.log(
                    LogType.UPLOAD,
                    LogLevel.ERROR,
                    "Worker 上传失败: ${file.fileName} - ${error.message}",
                )
            }
        }

        return if (hasFailure) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_FILE_ID = "file_id"
        const val KEY_PROCESS_ALL = "process_all"
    }
}
