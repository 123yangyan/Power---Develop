package com.owner.mindbody.worker

import android.app.NotificationChannel
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

/** 对齐 emotion deliverDailyCheckIn：通知 + 可选强弹窗 */
object MoodReminderDeliver {

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
            if (lastAt > 0 && now - lastAt < intervalMs) return
        }

        if (moodPrefs.isNotificationsEnabledSync()) {
            showNotification(context, moodPrefs.isStrongPopupSync())
        }
        if (moodPrefs.isStrongPopupSync() || force) {
            MoodCheckInActivity.launch(context)
        }
        moodPrefs.setLastReminderAt(System.currentTimeMillis())
    }

    private fun showNotification(context: Context, useFullScreenIntent: Boolean) {
        MoodReminderWorker.createChannel(context)

        val contentIntent = if (useFullScreenIntent) {
            Intent(context, MoodCheckInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.ROUTE_MOOD_RECORD)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MoodReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("记录此刻的状态")
            .setContentText("花十秒点选系统状态，顺着本性就好。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (useFullScreenIntent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        NotificationManagerCompat.from(context).notify(MoodReminderWorker.NOTIFICATION_ID, builder.build())
    }
}
