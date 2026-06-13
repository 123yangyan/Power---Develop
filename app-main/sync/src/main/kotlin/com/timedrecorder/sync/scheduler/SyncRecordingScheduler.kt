package com.timedrecorder.sync.scheduler

import com.timedrecorder.core.data.scheduler.RecordingScheduler
import com.timedrecorder.sync.schedule.ScheduleAlarmManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRecordingScheduler @Inject constructor(
    private val scheduleAlarmManager: ScheduleAlarmManager,
) : RecordingScheduler {
    override suspend fun rescheduleAllAlarms() {
        scheduleAlarmManager.rescheduleAll()
    }

    override fun cancelTaskAlarms(taskId: Long) {
        scheduleAlarmManager.cancelTaskAlarms(taskId)
    }
}
