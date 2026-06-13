package com.timedrecorder.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 文件清理 Worker：按保留天数删除过期本地录音。
 * 对应 PRD §7 保留天数默认 7 天。
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val audioFileRepository: AudioFileRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val logRepository: LogRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataSource.userPreferences.first()
        val retentionMillis = TimeUnit.DAYS.toMillis(prefs.retentionDays.toLong())
        val cutoff = System.currentTimeMillis() - retentionMillis

        val expiredFiles = audioFileRepository.getExpiredFiles(cutoff)
        var deletedCount = 0

        for (file in expiredFiles) {
            val physicalFile = File(file.filePath)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
            audioFileRepository.deleteFile(file)
            deletedCount++
        }

        if (deletedCount > 0) {
            logRepository.log(
                LogType.SYSTEM,
                LogLevel.INFO,
                "已清理 $deletedCount 个过期录音文件（保留 ${prefs.retentionDays} 天）",
            )
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "cleanup_worker"
    }
}
