package com.timedrecorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.RepeatType

/** Room 实体：task_schedule 表 */
@Entity(tableName = "task_schedule")
data class ScheduleTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "task_name")
    val taskName: String? = null,
    @ColumnInfo(name = "start_time")
    val startTimeMinutes: Int,
    @ColumnInfo(name = "end_time")
    val endTimeMinutes: Int,
    val enabled: Boolean = true,
    @ColumnInfo(name = "repeat_type")
    val repeatType: RepeatType = RepeatType.DAILY,
    @ColumnInfo(name = "slice_duration")
    val sliceDurationMinutes: Int = 5,
    @ColumnInfo(name = "audio_format")
    val audioFormat: AudioFormat = AudioFormat.M4A,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
