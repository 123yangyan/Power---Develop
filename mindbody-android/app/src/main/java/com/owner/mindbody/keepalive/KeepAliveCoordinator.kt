package com.owner.mindbody.keepalive

import android.content.Context
import android.content.Intent
import android.os.Build
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.HrStreamService
import com.owner.mindbody.util.AppLogger
import com.owner.mindbody.util.PowerKeepAlive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 前台保活唯一启停入口：RestartProtection、版本适配启动、状态聚合与 recover。
 */
object KeepAliveCoordinator {

    private const val TAG = "KeepAlive"

    private val _status = MutableStateFlow(KeepAliveStatus())
    val status: StateFlow<KeepAliveStatus> = _status.asStateFlow()

    fun start(context: Context, reason: KeepAliveReason) {
        if (!RestartProtection.allowStart()) {
            return
        }
        launchForegroundService(context)
        syncBleState(context)
        _status.update {
            it.copy(
                lastStartReason = reason,
                foregroundRunning = HrStreamService.isRunning,
            )
        }
        AppLogger.i(TAG, "start reason=$reason fgsRunning=${HrStreamService.isRunning}")
        refreshBatteryExempt(context)
    }

    /**
     * BLE 仍连接时不停止前台服务（HyperOS 回桌面易杀进程）。
     */
    fun stopIfBleDisconnected(context: Context, reason: KeepAliveReason) {
        val bleState = appOrNull(context)?.polarBleManager?.connectionState?.value
            ?: ConnectionState.DISCONNECTED
        if (bleState == ConnectionState.CONNECTED) {
            AppLogger.d(TAG, "stop skipped reason=$reason ble=CONNECTED")
            return
        }
        stop(context, reason)
    }

    fun stop(context: Context, reason: KeepAliveReason) {
        context.stopService(Intent(context, HrStreamService::class.java))
        syncBleState(context)
        _status.update {
            it.copy(
                lastStopReason = reason,
                foregroundRunning = false,
                wakeLockHeld = false,
            )
        }
        AppLogger.i(TAG, "stop reason=$reason")
        refreshBatteryExempt(context)
    }

    /** 页面恢复或心跳自检：BLE 已连但 FGS 未跑时尝试补偿启动。 */
    fun checkAndRecover(context: Context) {
        refreshBatteryExempt(context)
        val app = appOrNull(context) ?: return
        val bleState = app.polarBleManager.connectionState.value
        _status.update { it.copy(bleState = bleState, foregroundRunning = HrStreamService.isRunning) }
        if (bleState == ConnectionState.CONNECTED && !HrStreamService.isRunning) {
            AppLogger.w(TAG, "checkAndRecover: ble=CONNECTED fgs=false, restarting")
            start(context, KeepAliveReason.CHECK_RECOVER)
        }
    }

    fun setForegroundRunning(running: Boolean) {
        _status.update { it.copy(foregroundRunning = running) }
    }

    fun updateHeartbeat(
        bleState: ConnectionState,
        wakeLockHeld: Boolean,
        batteryExempt: Boolean,
        heartbeatSeq: Int,
        lastHrAgeMs: Long?,
    ) {
        val now = System.currentTimeMillis()
        _status.update {
            it.copy(
                foregroundRunning = HrStreamService.isRunning,
                bleState = bleState,
                wakeLockHeld = wakeLockHeld,
                batteryExempt = batteryExempt,
                lastHeartbeatMs = now,
                heartbeatSeq = heartbeatSeq,
            )
        }
        val hrPart = lastHrAgeMs?.let { age -> " lastHrAgeMs=$age" }.orEmpty()
        AppLogger.i(
            TAG,
            "heartbeat seq=$heartbeatSeq ble=$bleState fgs=${HrStreamService.isRunning} " +
                "wakeLock=$wakeLockHeld batteryExempt=$batteryExempt$hrPart"
        )
    }

    fun refreshBatteryExempt(context: Context) {
        val exempt = PowerKeepAlive.isIgnoringBatteryOptimizations(context)
        _status.update { it.copy(batteryExempt = exempt) }
    }

    private fun launchForegroundService(context: Context) {
        val intent = Intent(context, HrStreamService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "startForegroundService 被系统拦截: ${e.message}")
        }
    }

    private fun appOrNull(context: Context): MindBodyApplication? {
        return context.applicationContext as? MindBodyApplication
    }

    private fun syncBleState(context: Context) {
        val bleState = appOrNull(context)?.polarBleManager?.connectionState?.value
            ?: ConnectionState.DISCONNECTED
        _status.update { it.copy(bleState = bleState) }
    }
}
