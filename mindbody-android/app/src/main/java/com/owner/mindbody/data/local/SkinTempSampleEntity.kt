package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 在线皮肤温度流样本（BLE 实时同步）。 */
@Entity(
    tableName = "skin_temp_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"]),
        Index(value = ["timestamp", "syncState"])
    ]
)
data class SkinTempSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val temperatureC: Float,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
