package com.owner.mindbody.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.sync.SyncApiClient
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


/**
 * 生理状态通知按钮操作接收器。
 *
 * 处理「稍后提醒」和「今天不再」两个按钮，并向服务器回报用户响应。
 */
class PhysioNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.owner.mindbody.ACTION_PHYSIO_SNOOZE"
        const val ACTION_DISMISS = "com.owner.mindbody.ACTION_PHYSIO_DISMISS"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val TAG = "PhysioNotifReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        PhysioNotificationManager.cancel(context, notificationId)

        val app = context.applicationContext as? MindBodyApplication ?: return

        when (intent.action) {
            ACTION_SNOOZE -> {
                AppLogger.d(TAG, "Snooze tapped: notifId=$notificationId")
                scope.launch { reportResponse(app, notificationId, "snoozed") }
            }
            ACTION_DISMISS -> {
                AppLogger.d(TAG, "Dismiss tapped: notifId=$notificationId")
                scope.launch { reportResponse(app, notificationId, "dismissed") }
            }
        }
    }

    private suspend fun reportResponse(
        app: MindBodyApplication,
        notificationId: Int,
        response: String
    ) {
        val prefs = app.storage.syncPreferences
        val baseUrl = prefs.baseUrl.first()
        val apiKey = prefs.apiKey.first()
        val deviceId = prefs.getOrCreateDeviceId()
        if (baseUrl.isBlank()) return
        try {
            SyncApiClient(baseUrl, apiKey).reportNotificationResponse(
                deviceId = deviceId,
                notificationId = notificationId.takeIf { it >= 0 },
                response = response
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "reportNotificationResponse failed", e)
        }
    }
}
