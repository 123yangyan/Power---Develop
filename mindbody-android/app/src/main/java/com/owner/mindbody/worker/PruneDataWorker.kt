package com.owner.mindbody.worker

import android.content.Context
import androidx.work.*
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 已同步数据 7 天滚动清理 Worker。
 * 约束：充电 + 非低电量，每天执行一次。
 *
 * 安全红线：只删除 syncState = 'SYNCED' 的数据，
 * PENDING / FAILED 的数据无论多旧绝对不删。
 *
 * 为避免一次性大量删除导致 I/O 阻塞，
 * 每次 DELETE 带 LIMIT 5000，循环至无行可删。
 */
class PruneDataWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PruneDataWorker"
        private const val UNIQUE_WORK_NAME = "mindbody_prune_old_synced"
        private const val RETAIN_DAYS = 7L
        private const val BATCH_LIMIT = 5000

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PruneDataWorker>(
                1, TimeUnit.DAYS,
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
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? MindBodyApplication ?: return Result.failure()
        val storage = app.storage

        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETAIN_DAYS)
        val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cutoffMs)

        AppLogger.i(TAG, "开始清理 ${RETAIN_DAYS}天前已同步数据, cutoffMs=$cutoffMs, cutoffDate=$cutoffDate")

        var totalDeleted = 0

        // ── A 组：ms 时间戳表（高频，需分批循环） ──
        val msDaos = listOf<Pair<String, suspend () -> Int>>(
            "hr" to { storage.database.hrSampleDao().deleteSyncedBefore(cutoffMs) },
            "skin_temp" to { storage.database.skinTempSampleDao().deleteSyncedBefore(cutoffMs) },
            "ppi" to { storage.database.ppiSampleDao().deleteSyncedBefore(cutoffMs) },
            "acc_minute" to { storage.database.accMinuteSummaryDao().deleteSyncedBefore(cutoffMs) },
            "hr_247" to { storage.database.hr247SampleDao().deleteSyncedBefore(cutoffMs) },
            "ppi_247" to { storage.database.ppi247SampleDao().deleteSyncedBefore(cutoffMs) },
            "skin_temp_247" to { storage.database.skinTemp247SampleDao().deleteSyncedBefore(cutoffMs) },
            "activity_minute" to { storage.database.activityMinuteSampleDao().deleteSyncedBefore(cutoffMs) },
            "mood" to { storage.database.moodEntryDao().deleteSyncedBefore(cutoffMs) },
        )

        for ((name, deleteFn) in msDaos) {
            var tableDeleted = 0
            var batch: Int
            do {
                batch = deleteFn()
                tableDeleted += batch
            } while (batch >= BATCH_LIMIT)
            totalDeleted += tableDeleted
            if (tableDeleted > 0) {
                AppLogger.d(TAG, "  $name: 删除 $tableDeleted 行")
            }
        }

        // ── B 组：日期字符串表（低频，无需分批） ──
        val dateDaos = listOf<Pair<String, suspend () -> Int>>(
            "activity_day" to { storage.database.activityDaySummaryDao().deleteSyncedBeforeDate(cutoffDate) },
            "nightly_recharge" to { storage.database.nightlyRechargeDao().deleteSyncedBeforeDate(cutoffDate) },
            "sleep" to { storage.database.sleepSessionDao().deleteSyncedBeforeDate(cutoffDate) },
            "training" to { storage.database.trainingSessionDao().deleteSyncedBeforeDate(cutoffDate) },
        )

        for ((name, deleteFn) in dateDaos) {
            val deleted = deleteFn()
            totalDeleted += deleted
            if (deleted > 0) {
                AppLogger.d(TAG, "  $name: 删除 $deleted 行")
            }
        }

        AppLogger.i(TAG, "清理完成，共删除 $totalDeleted 行")
        return Result.success()
    }
}
