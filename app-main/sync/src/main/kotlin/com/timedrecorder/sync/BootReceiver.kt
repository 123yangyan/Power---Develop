package com.timedrecorder.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timedrecorder.sync.schedule.ScheduleAlarmManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开机广播接收器：恢复闹钟与服务。
 * 对应 PRD §12.1 开机自启。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduleAlarmManager: ScheduleAlarmManager
    @Inject lateinit var recordServiceController: RecordServiceController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        // 使用 goAsync 避免 ANR
        val pendingResult = goAsync()
        scope.launch {
            try {
                scheduleAlarmManager.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
