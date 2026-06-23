package com.owner.mindbody.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging 接收服务。
 *
 * 两个职责：
 * 1. `onNewToken` — 新 token 时向服务器注册，使服务端能发送推送。
 * 2. `onMessageReceived` — 接收到生理状态反馈推送后，调用
 *    [PhysioNotificationManager.show] 展示本地通知。
 *
 * 注意：此服务需要 `google-services.json` 和 Firebase BOM 依赖才能正常工作。
 * 未配置时 Firebase 不会初始化，此服务的回调不会被触发（无异常）。
 */
class PhysioFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PhysioFcmService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * FCM token 刷新时回调 — 注册到服务器。
     * 在设备首次安装、卸载重装或 token 过期时触发。
     */
    override fun onNewToken(token: String) {
        AppLogger.d(TAG, "FCM token refreshed: ${token.take(20)}…")
        val app = applicationContext as? MindBodyApplication ?: return
        scope.launch { FcmTokenRegistrar.registerToken(app, token) }
    }

    /**
     * 接收来自服务器的推送消息。
     *
     * 期望的 data payload（服务端 push_service.py 发送）：
     * ```json
     * {
     *   "notification_id": "123",
     *   "state_label":     "anxious",
     *   "message":         "你的身体似乎在提醒你放慢节奏…"
     * }
     * ```
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        AppLogger.d(TAG, "FCM message received: $data")

        val notificationId = data["notification_id"]?.toIntOrNull()
            ?: System.currentTimeMillis().toInt()
        val stateLabel = data["state_label"] ?: "elevated"
        val message = data["message"]
            ?: remoteMessage.notification?.body
            ?: return  // 无文本则静默忽略

        PhysioNotificationManager.show(
            context = applicationContext,
            notificationId = notificationId,
            stateLabel = stateLabel,
            message = message
        )
    }
}
