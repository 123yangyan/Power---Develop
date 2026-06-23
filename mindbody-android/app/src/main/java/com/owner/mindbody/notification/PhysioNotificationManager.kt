package com.owner.mindbody.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.owner.mindbody.MainActivity
import com.owner.mindbody.R

/**
 * 生理状态推送通知管理器。
 *
 * 通知渠道：`physio_feedback`（高优先级）
 * 三按钮操作：
 *  - 记录心情 → 打开 MainActivity（跳转到心情页）
 *  - 稍后提醒 → PhysioNotificationReceiver.ACTION_SNOOZE
 *  - 今天不再提醒 → PhysioNotificationReceiver.ACTION_DISMISS
 */
object PhysioNotificationManager {

    const val CHANNEL_ID = "physio_feedback"
    const val CHANNEL_NAME = "生理状态反馈"
    private const val CHANNEL_DESC = "基于实时心率变异度的身心状态提醒"

    /** 创建通知渠道（幂等，可在 Application.onCreate 调用）。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示生理状态反馈通知。
     *
     * @param context       应用 Context
     * @param notificationId  通知 ID（用于按钮回报 / 取消）
     * @param stateLabel    状态标签（"elevated" / "anxious" / "high_anxiety"）
     * @param message       LLM 生成的正文文本
     */
    fun show(
        context: Context,
        notificationId: Int,
        stateLabel: String,
        message: String
    ) {
        ensureChannel(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val titleEmoji = when (stateLabel) {
            "high_anxiety" -> "🔴"
            "anxious" -> "🟠"
            else -> "🟡"
        }
        val title = "$titleEmoji 身心状态提醒"

        // 点击通知主体 → 打开主页
        val openMainIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("nav_target", "physio_state")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 按钮 1：记录心情
        val logMoodPi = PendingIntent.getActivity(
            context,
            notificationId + 10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("nav_target", "mood_record")
                putExtra("notification_id", notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 按钮 2：稍后提醒
        val snoozePi = PendingIntent.getBroadcast(
            context,
            notificationId + 20,
            Intent(context, PhysioNotificationReceiver::class.java).apply {
                action = PhysioNotificationReceiver.ACTION_SNOOZE
                putExtra(PhysioNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 按钮 3：今天不再提醒
        val dismissPi = PendingIntent.getBroadcast(
            context,
            notificationId + 30,
            Intent(context, PhysioNotificationReceiver::class.java).apply {
                action = PhysioNotificationReceiver.ACTION_DISMISS
                putExtra(PhysioNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openMainIntent)
            .setAutoCancel(true)
            .addAction(0, "记录心情", logMoodPi)
            .addAction(0, "稍后提醒", snoozePi)
            .addAction(0, "今天不再", dismissPi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 未授权时静默忽略
        }
    }

    /** 取消指定 ID 的通知（用户点击按钮后收回）。 */
    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
