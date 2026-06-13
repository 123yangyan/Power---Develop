package com.timedrecorder.core.data.scheduler

/** 录音闹钟调度接口，由 sync 模块实现 */
interface RecordingScheduler {
    suspend fun rescheduleAllAlarms()
    fun cancelTaskAlarms(taskId: Long)
}
