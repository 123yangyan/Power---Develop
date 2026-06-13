package com.owner.mindbody.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owner.mindbody.MindBodyApplication

/** 定时检查是否该弹出心情记录提醒。 */
class MoodReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MindBodyApplication ?: return Result.success()
        MoodReminderDeliver.deliver(applicationContext, force = false)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "mood_reminder"
        const val NOTIFICATION_ID = 2001

        fun createChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "心情记录提醒",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒记录价值感与耗能坐标"
            }
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}

/** 测试提醒：忽略静默与间隔 */
class MoodReminderTestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        MoodReminderDeliver.deliver(applicationContext, force = true)
        return Result.success()
    }
}
