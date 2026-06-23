package com.owner.mindbody.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.sync.SyncApiClient
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 主动拉取 FCM token 并向服务器注册。
 *
 * 补充 [PhysioFcmService.onNewToken]：后者仅在 token 刷新时触发，
 * 启动时主动 fetch 可避免「先拿 token、后填 Server URL」导致永不注册。
 */
object FcmTokenRegistrar {

    private const val TAG = "FcmTokenRegistrar"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleStartupRegistration(app: MindBodyApplication) {
        scope.launch { fetchAndRegister(app) }
    }

    suspend fun fetchAndRegister(app: MindBodyApplication) {
        AppLogger.d(TAG, "Proactively fetching FCM token…")
        val token = fetchToken() ?: return
        registerToken(app, token)
    }

    suspend fun registerToken(app: MindBodyApplication, token: String) {
        val prefs = app.storage.syncPreferences
        val baseUrl = prefs.baseUrl.first()
        val apiKey = prefs.apiKey.first()
        if (baseUrl.isBlank()) {
            AppLogger.d(
                TAG,
                "Sync URL not configured, defer registration (token prefix=${token.take(20)}…)"
            )
            return
        }
        val deviceId = prefs.getOrCreateDeviceId()
        AppLogger.d(TAG, "Registering FCM token with server…")
        val ok = SyncApiClient(baseUrl, apiKey).registerFcmToken(deviceId, token)
        if (ok) {
            AppLogger.i(TAG, "FCM token registered: true")
        } else {
            AppLogger.w(TAG, "FCM token registered: false")
        }
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                AppLogger.d(TAG, "FCM token fetched: ${token.take(20)}…")
                cont.resume(token)
            } else {
                val err = task.exception?.message ?: "unknown"
                AppLogger.w(TAG, "FCM token fetch failed: $err")
                cont.resume(null)
            }
        }
    }
}
