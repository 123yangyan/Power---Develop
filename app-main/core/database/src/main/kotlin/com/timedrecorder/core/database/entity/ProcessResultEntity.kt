package com.timedrecorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.timedrecorder.core.model.RiskLevel

/** Room 实体：process_result 表（file_id 唯一，轮询时 upsert） */
@Entity(
    tableName = "process_result",
    indices = [Index(value = ["file_id"], unique = true)],
)
data class ProcessResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "file_id")
    val fileId: Long,
    val transcript: String? = null,
    val title: String? = null,
    val summary: String?,
    @ColumnInfo(name = "keywords_json")
    val keywordsJson: String,
    @ColumnInfo(name = "alert_flag")
    val alertFlag: Boolean,
    @ColumnInfo(name = "risk_level")
    val riskLevel: RiskLevel?,
    @ColumnInfo(name = "result_json")
    val resultJson: String?,
    @ColumnInfo(name = "processed_at")
    val processedAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
