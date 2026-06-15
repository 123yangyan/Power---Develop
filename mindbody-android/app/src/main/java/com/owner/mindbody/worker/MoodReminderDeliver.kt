package com.owner.mindbody.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.owner.mindbody.MainActivity
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.MoodCheckInActivity
import com.owner.mindbody.R
import com.owner.mindbody.util.AppForegroundHelper

/**
 * 定时探查投递：
 * - 强弹窗：FullScreenIntent 高优先级通知（合规路径，禁止 Worker 内 startActivity）
 * - 弱提醒：普通通知，点击进入记录页
 */
object MoodReminderDeliver {

    private const val NOTIFICATION_TITLE = "内在剧场 · 探查一下"
    private const val NOTIFICATION_TEXT = "点选一个情绪角色，1 秒完成记录"

    private const val REQUEST_CONTENT = 100
    private const val REQUEST_FULL_SCREEN = 101
    private const val REQUEST_SNOOZE = 102

    suspend fun deliver(context: Context, force: Boolean = false) {
        val app = context.applicationContext as? MindBodyApplication ?: return
        val moodPrefs = app.moodPreferences

        if (!force) {
            if (!moodPrefs.isNotificationsEnabledSync() && !moodPrefs.isStrongPopupSync()) return
            val quietStart = moodPrefs.getQuietStartSync()
            val quietEnd = moodPrefs.getQuietEndSync()
            if (moodPrefs.isQuietHours(moodPrefs.currentMinutesOfDay(), quietStart, quietEnd)) return

            val intervalMs = moodPrefs.getEffectiveIntervalMs()
            val lastAt = moodPrefs.getLastReminderAtSync()
            val now = System.currentTimeMillis()
            if (lastAt > 0 && now - lastAt < intervalMs) {
                MoodReminderScheduler.scheduleNextExact(context, lastAt + intervalMs - now)
                return
            }
        }

        val useStrongPopup = moodPrefs.isStrongPopupSync() || force

        if (useStrongPopup) {
            // 统一走通知通道；soft dismiss 经 Intent extra 传递（BAL 合规，targetSdk 35）
            val delivered = showStrongProbeNotification(
                context,
                softDismiss = AppForegroundHelper.canSoftDismissProbe(context)
            )
            if (delivered) {
                val deliveredAt = System.currentTimeMillis()
                moodPrefs.setLastReminderAt(deliveredAt)
                MoodReminderScheduler.scheduleNextExact(context, moodPrefs.getEffectiveIntervalMs())
            }
        } else if (moodPrefs.isNotificationsEnabledSync()) {
            if (showWeakNotification(context)) {
                val deliveredAt = System.currentTimeMillis()
                moodPrefs.setLastReminderAt(deliveredAt)
                MoodReminderScheduler.scheduleNextExact(context, moodPrefs.getEffectiveIntervalMs())
            }
        }
    }

    /**
     * 强探查：息屏 FullScreenIntent 亮屏弹 Sheet；亮屏 Heads-up + 「稍后」Action。
     * @return 是否成功 post 通知
     */
    private fun showStrongProbeNotification(context: Context, softDismiss: Boolean): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        MoodReminderWorker.createChannel(context)

        val checkInIntent = Intent(context, MoodCheckInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MoodCheckInActivity.EXTRA_SOFT_DISMISS, softDismiss)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            REQUEST_FULL_SCREEN,
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentPending = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePending = PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            Intent(context, MoodReminderSnoozeReceiver::class.java).apply {
                action = MoodReminderSnoozeReceiver.ACTION_SNOOZE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MoodReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher, "稍后", snoozePending)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fullScreenPending, true)
        }

        notificationManager.notify(MoodReminderWorker.NOTIFICATION_ID, builder.build())
        return true
    }

    /** 弱提醒：点击通知进入记录页 */
    private fun showWeakNotification(context: Context): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        MoodReminderWorker.createChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.ROUTE_MOOD_RECORD)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MoodReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(MoodReminderWorker.NOTIFICATION_ID, notification)
        return true
    }

    /** Android 14+ 是否允许 FullScreenIntent（锁屏自动弹探查） */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    const val FULL_SCREEN_INTENT_HINT =
        "请在系统设置中允许本 App 全屏通知，锁屏才能自动弹出探查"
}
