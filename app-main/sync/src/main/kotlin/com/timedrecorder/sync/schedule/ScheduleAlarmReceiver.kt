package com.timedrecorder.sync.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timedrecorder.sync.RecordServiceController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 接收 AlarmManager 触发的开始/结束广播。
 */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var recordServiceController: RecordServiceController

    @Inject
    lateinit var scheduleAlarmManager: ScheduleAlarmManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(RecordServiceController.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        when (intent.action) {
            RecordServiceController.ACTION_START -> {
                recordServiceController.startRecording(taskId)
            }
            RecordServiceController.ACTION_STOP -> {
                recordServiceController.stopRecording(taskId)
            }
        }

        // 关键：闹钟是一次性的，每次触发后立刻把「下一次（明天）」的闹钟重新挂上，
        // 这样录音任务才能每天自动重复。用 goAsync 让广播在协程完成前不被系统回收。
        val pendingResult = goAsync()
        scope.launch {
            try {
                scheduleAlarmManager.rescheduleTask(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
