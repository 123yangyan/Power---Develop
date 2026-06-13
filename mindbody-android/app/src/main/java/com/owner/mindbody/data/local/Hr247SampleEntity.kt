package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 设备 24/7 心率样本（约 5 分钟 / 高强度 1 分钟间隔）。 */
@Entity(
    tableName = "hr_247_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class Hr247SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val triggerType: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
