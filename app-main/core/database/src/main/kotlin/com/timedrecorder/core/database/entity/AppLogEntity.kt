package com.timedrecorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType

/** Room 实体：app_log 表 */
@Entity(tableName = "app_log")
data class AppLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "log_type")
    val logType: LogType,
    @ColumnInfo(name = "log_level")
    val logLevel: LogLevel,
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
