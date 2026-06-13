package com.timedrecorder.core.model

/**
 * 录音计划任务，对应 PRD §13.1 task_schedule。
 *
 * @param startTimeMinutes 开始时间（距 0 点的分钟数，如 09:00 = 540）
 * @param endTimeMinutes 结束时间（距 0 点的分钟数，如 12:00 = 720）
 * @param sliceDurationMinutes 切片时长（分钟），默认 5
 */
data class ScheduleTask(
    val id: Long = 0,
    val taskName: String? = null,
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
    val enabled: Boolean = true,
    val repeatType: RepeatType = RepeatType.DAILY,
    val sliceDurationMinutes: Int = 5,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** 格式化为 HH:mm 显示 */
    fun formatStartTime(): String = formatMinutes(startTimeMinutes)

    fun formatEndTime(): String = formatMinutes(endTimeMinutes)

    companion object {
        fun formatMinutes(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return "%02d:%02d".format(h, m)
        }
    }
}
