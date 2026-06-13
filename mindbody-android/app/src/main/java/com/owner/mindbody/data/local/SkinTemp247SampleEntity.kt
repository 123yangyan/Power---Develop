package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 设备 24/7 皮肤温度样本（约 5 分钟间隔）。 */
@Entity(
    tableName = "skin_temp_247_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class SkinTemp247SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val temperatureC: Float,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
