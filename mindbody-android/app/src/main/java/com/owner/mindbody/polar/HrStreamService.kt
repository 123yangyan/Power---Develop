package com.owner.mindbody.polar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.owner.mindbody.MainActivity
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.R
import com.owner.mindbody.worker.PpiStreamWorker
import com.owner.mindbody.worker.PpiStreamWorker.Companion.StreamAttemptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：BLE 连接后保持进程存活；
 * 每 90 秒将 PPI 窗口推送到分析服务端。
 */
class HrStreamService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamLoopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 记录服务启动时刻作为 BLE warm-up 基准。
     * Service 在 BLE 连接成功后不久启动（见 PolarBleManager.deviceConnected），
     * 以此时刻作为 warm-up 起点，避免连接初期信号不稳时推送无效窗口。
     */
    private var serviceStartedAtMs: Long = 0L

    companion object {
        private const val CHANNEL_ID = "hr_stream"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "MindBody:HrStream"

        fun start(context: Context) {
            val intent = Intent(context, HrStreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HrStreamService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartedAtMs = System.currentTimeMillis()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startStreamLoopIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        streamLoopJob?.cancel()
        streamLoopJob = null
        releaseWakeLock()
        super.onDestroy()
        val app = applicationContext as? MindBodyApplication ?: return
        serviceScope.launch {
            app.storage.flushAll()
            serviceScope.cancel()
        }
    }

    private fun startStreamLoopIfNeeded() {
        if (streamLoopJob?.isActive == true) return
        streamLoopJob = serviceScope.launch {
            val app = applicationContext as? MindBodyApplication ?: return@launch
            // 游标从服务启动时刻起，覆盖 BLE 预热期采集的数据，避免固定 lookback 造成时间空洞
            var lastWindowEndMs = serviceStartedAtMs
            while (isActive) {
                val now = System.currentTimeMillis()
                val result = PpiStreamWorker.tryStreamOnce(
                    app,
                    sinceMs = lastWindowEndMs,
                    bleConnectedAtMs = serviceStartedAtMs,
                )
                if (result != StreamAttemptResult.SKIPPED_EARLY_GATE) {
                    lastWindowEndMs = now
                }
                delay(PpiStreamWorker.STREAM_INTERVAL_MS)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hr_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.ROUTE_HEART_RATE)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.hr_notification_title))
            .setContentText(getString(R.string.hr_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
