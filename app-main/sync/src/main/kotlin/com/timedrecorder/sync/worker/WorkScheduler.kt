package com.timedrecorder.sync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.scheduler.UploadScheduler
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一调度后台 Worker：上传、轮询、清理。
 * V1.1 T1/T10：实现 UploadScheduler 接口，修复 WiFi 专属上传约束。
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val logRepository: LogRepository,
) : UploadScheduler {

    private val workManager = WorkManager.getInstance(context)

    /** 将指定文件加入上传队列 */
    override fun enqueueUpload(fileId: Long) {
        val wifiOnly = runBlocking { preferencesDataSource.userPreferences.first().wifiOnlyUpload }
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_FILE_ID to fileId))
            .setConstraints(uploadNetworkConstraints())
            .build()
        workManager.enqueueUniqueWork("upload_$fileId", ExistingWorkPolicy.REPLACE, request)
        runBlocking {
            val networkHint = if (wifiOnly) "仅Wi-Fi" else "任意网络"
            logRepository.log(
                LogType.UPLOAD,
                LogLevel.INFO,
                "上传任务已入队: upload_$fileId（$networkHint）",
            )
            if (wifiOnly) {
                logRepository.log(
                    LogType.UPLOAD,
                    LogLevel.INFO,
                    "已加入队列，需连接 Wi-Fi 后上传",
                )
            }
        }
    }

    override fun enqueuePoll(serverFileId: String, localFileId: Long) {
        val prefs = runBlocking { preferencesDataSource.userPreferences.first() }
        enqueuePollWithOptions(
            serverFileId = serverFileId,
            localFileId = localFileId,
            maxAttempts = prefs.pollMaxAttempts,
            intervalSeconds = prefs.pollIntervalSeconds,
        )
    }

    /** 上传成功后启动结果轮询（指定轮询参数） */
    fun enqueuePollWithOptions(
        serverFileId: String,
        localFileId: Long,
        maxAttempts: Int,
        intervalSeconds: Int,
    ) {
        val request = OneTimeWorkRequestBuilder<PollResultWorker>()
            .setInputData(
                workDataOf(
                    PollResultWorker.KEY_SERVER_FILE_ID to serverFileId,
                    PollResultWorker.KEY_LOCAL_FILE_ID to localFileId,
                    PollResultWorker.KEY_MAX_ATTEMPTS to maxAttempts,
                    PollResultWorker.KEY_INTERVAL_SECONDS to intervalSeconds,
                ),
            )
            .setConstraints(pollNetworkConstraints())
            .build()
        workManager.enqueueUniqueWork("poll_$localFileId", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * T1：重试所有待上传文件。
     * 实现 UploadScheduler 接口，由 FilesViewModel 通过接口调用。
     */
    override fun enqueuePendingUploads() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_PROCESS_ALL to true))
            .setConstraints(uploadNetworkConstraints())
            .build()
        workManager.enqueueUniqueWork("upload_pending", ExistingWorkPolicy.REPLACE, request)
        runBlocking {
            logRepository.log(LogType.UPLOAD, LogLevel.INFO, "批量上传任务已入队: upload_pending")
        }
    }

    /** 取消指定文件的上传与结果轮询任务（删除录音前调用） */
    override fun cancelFileWork(localFileId: Long) {
        workManager.cancelUniqueWork("upload_$localFileId")
        workManager.cancelUniqueWork("poll_$localFileId")
    }

    /** 注册每日文件清理任务 */
    fun scheduleDailyCleanup() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(
            CleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * T10：上传网络约束。
     * wifiOnlyUpload=true 时仅 Wi-Fi；否则任意网络。
     */
    private fun uploadNetworkConstraints(): Constraints {
        val wifiOnly = runBlocking { preferencesDataSource.userPreferences.first().wifiOnlyUpload }
        return Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
    }

    /** 轮询约束：有网即可，不受「仅 Wi-Fi 上传」限制 */
    private fun pollNetworkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
