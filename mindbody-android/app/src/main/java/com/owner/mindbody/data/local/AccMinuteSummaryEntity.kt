package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 加速度每分钟聚合摘要。
 * 原始 ACC 约 200Hz，不适合逐条落库，这里只存每分钟统计值。
 */
@Entity(
    tableName = "acc_minute_summary",
    indices = [
        Index(value = ["minuteTimestamp"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class AccMinuteSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val minuteTimestamp: Long,
    val avgMagnitudeMg: Float,
    val maxMagnitudeMg: Float,
    val sampleCount: Int,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = minuteTimestamp, updatedAt = minuteTimestamp)
)
