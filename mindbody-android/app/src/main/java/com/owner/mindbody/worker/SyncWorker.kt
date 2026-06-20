package com.owner.mindbody.worker

import android.content.Context
import androidx.work.*
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 云端身心数据同步 Worker。
 * 约束：联网 + 电量不低于低电量 + 同步开关已启用。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val UNIQUE_WORK_NAME = "mindbody_cloud_sync"
        private const val DEFAULT_INTERVAL_HOURS = 2L

        fun schedulePeriodic(context: Context, intervalHours: Long = DEFAULT_INTERVAL_HOURS) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** 手动触发一次立即同步 */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? MindBodyApplication ?: return Result.failure()
        val storage = app.storage
        val sync = storage.sync
        val prefs = storage.syncPreferences

        // 检查开关
        val enabled = prefs.syncEnabled.first()
        if (!enabled) {
            AppLogger.d(TAG, "Sync disabled, skip")
            return Result.success()
        }

        // 初始化 API 客户端
        val baseUrl = prefs.baseUrl.first()
        if (baseUrl.isBlank()) {
            AppLogger.d(TAG, "Sync config incomplete (URL missing), skip")
            return Result.success()
        }
        val apiKey = prefs.apiKey.first()

        sync.apiClient = com.owner.mindbody.data.sync.SyncApiClient(baseUrl, apiKey)

        AppLogger.i(TAG, "Starting cloud sync...")
        storage.flushAll()
        val result = sync.syncOnce()

        return if (result.error != null) {
            AppLogger.w(TAG, "Sync completed with errors: ${result.error}")
            Result.retry()
        } else {
            AppLogger.i(TAG, "Sync OK: uploaded=${result.uploaded} failed=${result.failed} skipped=${result.skipped}")
            Result.success()
        }
    }
}
