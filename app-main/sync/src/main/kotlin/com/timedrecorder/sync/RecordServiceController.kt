package com.timedrecorder.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import com.timedrecorder.core.data.record.RecordingController
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.sync.record.RecordService
import com.timedrecorder.sync.schedule.ScheduleAlarmManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 录音服务对外控制器，封装 start/stop/pause/resume Intent。
 * 实现 RecordingController，供首页控制手动与计划录音。
 */
@Singleton
class RecordServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleAlarmManager: ScheduleAlarmManager,
    private val logRepository: LogRepository,
) : RecordingController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动指定计划任务的录音前台服务 */
    fun startRecording(taskId: Long) {
        scope.launch {
            val task = scheduleRepository.getTaskById(taskId)
            if (task == null || !task.enabled) {
                logRepository.log(LogType.RECORDING, LogLevel.WARN, "任务不存在或未启用: $taskId")
                return@launch
            }
            logRepository.log(
                LogType.RECORDING,
                LogLevel.INFO,
                "开始录音: ${task.taskName ?: task.formatStartTime()}",
            )
            sendIntent(ACTION_START, taskId)
        }
    }

    /** 停止指定计划任务的录音 */
    fun stopRecording(taskId: Long) {
        scope.launch {
            logRepository.log(LogType.RECORDING, LogLevel.INFO, "停止录音: taskId=$taskId")
            sendServiceIntent(ACTION_STOP, taskId)

            val task = scheduleRepository.getTaskById(taskId)
            if (task != null && task.enabled) {
                scheduleAlarmManager.scheduleTaskAlarms(task)
            }
        }
    }

    override fun startManualRecording(scenario: RecordingScenario) {
        scope.launch {
            logRepository.log(
                LogType.RECORDING,
                LogLevel.INFO,
                "开始手动录音: 场景=${scenario.displayName} (taskId=0)",
            )
            sendIntent(ACTION_START, MANUAL_TASK_ID, scenario)
        }
    }

    override fun pauseActiveRecording() {
        scope.launch {
            logRepository.log(LogType.RECORDING, LogLevel.INFO, "暂停录音")
            sendServiceIntent(ACTION_PAUSE, ANY_ACTIVE_TASK_ID)
        }
    }

    override fun resumeActiveRecording() {
        scope.launch {
            logRepository.log(LogType.RECORDING, LogLevel.INFO, "继续录音")
            sendServiceIntent(ACTION_RESUME, ANY_ACTIVE_TASK_ID)
        }
    }

    override fun stopActiveRecording() {
        scope.launch {
            logRepository.log(LogType.RECORDING, LogLevel.INFO, "停止当前录音会话")
            sendServiceIntent(ACTION_STOP, ANY_ACTIVE_TASK_ID)
        }
    }

    override fun cancelActiveRecording() {
        scope.launch {
            logRepository.log(LogType.RECORDING, LogLevel.INFO, "放弃当前录音会话")
            sendServiceIntent(ACTION_CANCEL, ANY_ACTIVE_TASK_ID)
        }
    }

    /** 重新注册所有闹钟（设置变更或开机后调用） */
    fun rescheduleAllAlarms() {
        scope.launch { scheduleAlarmManager.rescheduleAll() }
    }

    private fun sendIntent(action: String, taskId: Long, scenario: RecordingScenario? = null) {
        val intent = Intent(context, RecordService::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            scenario?.let { putExtra(EXTRA_SCENARIO_ID, it.id) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun sendServiceIntent(action: String, taskId: Long) {
        val intent = Intent(context, RecordService::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    companion object {
        const val ACTION_START = "com.timedrecorder.action.START_RECORDING"
        const val ACTION_STOP = "com.timedrecorder.action.STOP_RECORDING"
        const val ACTION_PAUSE = "com.timedrecorder.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.timedrecorder.action.RESUME_RECORDING"
        const val ACTION_CANCEL = "com.timedrecorder.action.CANCEL_RECORDING"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SCENARIO_ID = "extra_scenario_id"

        /** 手动录音任务 ID 约定为 0 */
        const val MANUAL_TASK_ID = 0L

        /** 表示对当前活跃会话操作，不指定具体 taskId */
        const val ANY_ACTIVE_TASK_ID = -1L
    }
}
