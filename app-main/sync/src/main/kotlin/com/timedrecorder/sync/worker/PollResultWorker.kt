package com.timedrecorder.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.data.util.AlertEvaluator
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.sync.notification.AlertNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

/**
 * 结果轮询 Worker：默认 30 秒 × 10 次。
 * 对应 PRD §7 结果获取方式。
 */
@HiltWorker
class PollResultWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resultRepository: ResultRepository,
    private val audioFileRepository: AudioFileRepository,
    private val logRepository: LogRepository,
    private val alertNotifier: AlertNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val serverFileId = inputData.getString(KEY_SERVER_FILE_ID) ?: return Result.failure()
        val localFileId = inputData.getLong(KEY_LOCAL_FILE_ID, -1L)
        val maxAttempts = inputData.getInt(KEY_MAX_ATTEMPTS, 10)
        val intervalSeconds = inputData.getInt(KEY_INTERVAL_SECONDS, 30)

        logRepository.log(
            LogType.NETWORK,
            LogLevel.INFO,
            "开始轮询结果: serverFileId=$serverFileId, localFileId=$localFileId, 最多${maxAttempts}次",
        )

        repeat(maxAttempts) { attempt ->
            val pollResult = resultRepository.pollResult(serverFileId, localFileId)
            pollResult.onSuccess { result ->
                if (result.processedAt != null) {
                    // 处理完成，检查是否需要通知
                    if (AlertEvaluator.shouldNotify(result.alertFlag, result.keywords, result.riskLevel)) {
                        alertNotifier.showAlertNotification(
                            title = "录音异常提醒",
                            content = result.summary ?: "检测到异常关键词",
                            fileId = localFileId,
                        )
                    }
                    return Result.success()
                }
            }

            // 检查处理状态
            val file = audioFileRepository.getFileById(localFileId)
            if (file?.processStatus == ProcessStatus.COMPLETED) {
                return Result.success()
            }
            if (file?.processStatus == ProcessStatus.FAILED) {
                return Result.failure()
            }

            if (attempt < maxAttempts - 1) {
                delay(intervalSeconds * 1000L)
            }
        }

        // 超过最大轮询次数，标记失败
        audioFileRepository.updateProcessStatus(localFileId, ProcessStatus.FAILED)
        logRepository.log(
            LogType.NETWORK,
            LogLevel.WARN,
            "轮询超时: serverFileId=$serverFileId, 已尝试${maxAttempts}次",
        )
        return Result.failure()
    }

    companion object {
        const val KEY_SERVER_FILE_ID = "server_file_id"
        const val KEY_LOCAL_FILE_ID = "local_file_id"
        const val KEY_MAX_ATTEMPTS = "max_attempts"
        const val KEY_INTERVAL_SECONDS = "interval_seconds"
    }
}
