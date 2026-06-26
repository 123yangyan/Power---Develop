package com.owner.mindbody.polar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.owner.mindbody.MainActivity
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.R
import com.owner.mindbody.keepalive.KeepAliveConfig
import com.owner.mindbody.keepalive.KeepAliveCoordinator
import com.owner.mindbody.util.AppLogger
import com.owner.mindbody.util.CompanionDeviceHelper
import com.owner.mindbody.util.PowerKeepAlive
import com.owner.mindbody.worker.PpiStreamWorker
import com.owner.mindbody.worker.PpiStreamWorker.Companion.StreamAttemptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：BLE 连接后保持进程存活；
 * 每 90 秒将 PPI 窗口推送到分析服务端。
 */
class HrStreamService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamLoopJob: Job? = null
    private var heartbeatJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var heartbeatSeq = 0

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
        private const val TAG = "KeepAlive"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        KeepAliveCoordinator.setForegroundRunning(true)
        serviceStartedAtMs = System.currentTimeMillis()
        AppLogger.i(TAG, "HrStreamService onCreate")
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
        startHeartbeatLoopIfNeeded()
        verifyCompanionAssociationAsync()
    }

    private fun verifyCompanionAssociationAsync() {
        serviceScope.launch {
            val app = applicationContext as? MindBodyApplication ?: return@launch
            val deviceId = app.devicePreferences.savedDeviceId.first()
            val result = CompanionDeviceHelper.verifyAssociation(app, deviceId)
            if (result.valid) {
                AppLogger.i(
                    TAG,
                    "CDM verified mac=${result.macAddress} associationId=${result.associationId}",
                )
            } else {
                AppLogger.w(TAG, "CDM not associated: ${result.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "HrStreamService onStartCommand startId=$startId flags=$flags")
        startStreamLoopIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "HrStreamService onDestroy")
        heartbeatJob?.cancel()
        heartbeatJob = null
        streamLoopJob?.cancel()
        streamLoopJob = null
        releaseWakeLock()
        isRunning = false
        KeepAliveCoordinator.setForegroundRunning(false)
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

    private fun startHeartbeatLoopIfNeeded() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                emitHeartbeat()
                delay(KeepAliveConfig.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun emitHeartbeat() {
        val app = applicationContext as? MindBodyApplication ?: return
        val polar = app.polarBleManager
        val bleState = polar.connectionState.value
        val wakeHeld = wakeLock?.isHeld == true
        val batteryExempt = PowerKeepAlive.isIgnoringBatteryOptimizations(this)
        val lastHrMs = polar.lastHrSampleMs.value
        val lastHrAgeMs = if (lastHrMs > 0L) {
            System.currentTimeMillis() - lastHrMs
        } else {
            null
        }
        heartbeatSeq += 1
        KeepAliveCoordinator.updateHeartbeat(
            bleState = bleState,
            wakeLockHeld = wakeHeld,
            batteryExempt = batteryExempt,
            heartbeatSeq = heartbeatSeq,
            lastHrAgeMs = lastHrAgeMs,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            AppLogger.w("HrStreamService", "acquireWakeLock failed: ${e.message}")
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
