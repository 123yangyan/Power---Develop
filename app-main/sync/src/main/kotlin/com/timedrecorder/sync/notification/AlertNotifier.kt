package com.timedrecorder.sync.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.datastore.UserPreferences
import com.timedrecorder.sync.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 异常/关键词系统通知，对应 PRD §9.6。
 * V1.1 T8：在 sendAlert 前检查静音时段，静音期间不推系统通知（但仍写入消息中心）。
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
) {
    init {
        createNotificationChannel()
    }

    /** 弹出异常提醒通知（T8：静音时段内跳过系统通知） */
    fun showAlertNotification(title: String, content: String, fileId: Long) {
        val prefs = runBlocking { preferencesDataSource.userPreferences.first() }
        if (!prefs.notificationEnabled || !prefs.alertEnabled) return

        // T8：检查当前是否处于静音时段
        if (isInQuietHours(prefs)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(fileId.toInt(), notification)
    }

    /**
     * T8：判断当前时间是否处于静音时段。
     *
     * 逻辑：支持跨午夜时段（如 22:00–07:00）：
     *   - start > end：说明跨午夜，[currentMin >= start OR currentMin <= end] 时静音
     *   - start <= end：同一天内，[start <= currentMin <= end] 时静音
     */
    private fun isInQuietHours(prefs: UserPreferences): Boolean {
        if (!prefs.quietModeEnabled) return false
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = prefs.quietStartMinutes
        val end = prefs.quietEndMinutes
        return if (start > end) {
            // 跨午夜（如 22:00–07:00）
            currentMinutes >= start || currentMinutes <= end
        } else {
            // 同一天内（如 10:00–18:00）
            currentMinutes in start..end
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "异常提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "录音异常/关键词识别提醒" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alert_notifications"
    }
}
