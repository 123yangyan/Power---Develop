package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 心率样本实体，对应规划中的 hr_samples 表。
 * timestamp 为 Unix 毫秒时间戳；bpm 为每分钟心跳次数。
 */
@Entity(
    tableName = "hr_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"]),
        Index(value = ["timestamp", "syncState"])
    ]
)
data class HrSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val rrMs: Int? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
