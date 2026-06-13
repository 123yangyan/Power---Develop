package com.timedrecorder.sync.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.ScheduleTask
import com.timedrecorder.sync.RecordServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 AlarmManager 注册录音开始/结束闹钟。
 * 对应 PRD §12.2 定时策略。
 */
@Singleton
class ScheduleAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val logRepository: LogRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 为所有已启用任务重新注册闹钟 */
    suspend fun rescheduleAll() {
        val tasks = scheduleRepository.observeEnabledTasks().first()
        tasks.forEach { task ->
            scheduleTaskAlarms(task)
        }
        logRepository.log(LogType.SYSTEM, LogLevel.INFO, "已重新注册 ${tasks.size} 个录音闹钟")
    }

    /** 为单个任务注册开始和结束闹钟 */
    fun scheduleTaskAlarms(task: ScheduleTask) {
        scheduleAlarm(task, isStart = true)
        scheduleAlarm(task, isStart = false)
    }

    /**
     * 按任务 ID 重新注册「下一次」闹钟，用于实现每日重复。
     * AlarmManager.setExact 是一次性闹钟，每次被触发后必须重新挂上下一次，
     * 否则任务只会执行一次。这里在闹钟响铃后由 Receiver 调用本方法续期。
     */
    suspend fun rescheduleTask(taskId: Long) {
        // 取出最新任务配置；任务被删除或已停用则不再续期
        val task = scheduleRepository.getTaskById(taskId) ?: return
        if (!task.enabled) return
        scheduleTaskAlarms(task)
    }

    /** 取消单个任务的所有闹钟 */
    fun cancelTaskAlarms(taskId: Long) {
        alarmManager.cancel(createPendingIntent(taskId, isStart = true))
        alarmManager.cancel(createPendingIntent(taskId, isStart = false))
    }

    private fun scheduleAlarm(task: ScheduleTask, isStart: Boolean) {
        val triggerTime = computeNextTriggerTime(
            minutesOfDay = if (isStart) task.startTimeMinutes else task.endTimeMinutes,
        )
        val pendingIntent = createPendingIntent(task.id, isStart)
        // 使用 setExactAndAllowWhileIdle 提高 Doze 模式下触发可靠性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent,
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /** 计算下一次触发时间（今日或明日） */
    private fun computeNextTriggerTime(minutesOfDay: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun createPendingIntent(taskId: Long, isStart: Boolean): PendingIntent {
        val action = if (isStart) RecordServiceController.ACTION_START else RecordServiceController.ACTION_STOP
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(RecordServiceController.EXTRA_TASK_ID, taskId)
        }
        val requestCode = (taskId * 2 + if (isStart) 0 else 1).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
