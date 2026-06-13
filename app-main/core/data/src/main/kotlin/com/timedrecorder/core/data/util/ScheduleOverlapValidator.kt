package com.timedrecorder.core.data.util

import com.timedrecorder.core.model.ScheduleTask

/**
 * 录音任务时间段重叠校验工具。
 * PRD §9.2：不同任务时间段不允许重叠（含边界重合）。
 */
object ScheduleOverlapValidator {
    /**
     * 检测新任务是否与已有任务重叠。
     *
     * @param newTask 待保存的任务
     * @param existingTasks 已有任务列表
     * @return 与之重叠的任务，无重叠返回 null
     */
    fun findOverlap(newTask: ScheduleTask, existingTasks: List<ScheduleTask>): ScheduleTask? {
        return existingTasks
            .filter { it.id != newTask.id }
            .firstOrNull { overlaps(newTask, it) }
    }

    /** 判断两个时间段是否重叠（含边界重合） */
    fun overlaps(a: ScheduleTask, b: ScheduleTask): Boolean {
        // 区间 [start, end) 重叠判定：a.start < b.end && b.start < a.end
        // 边界重合也算重叠：09:00-12:00 与 12:00-14:00 视为重叠
        return a.startTimeMinutes <= b.endTimeMinutes && b.startTimeMinutes <= a.endTimeMinutes
    }
}
