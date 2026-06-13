package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 设备 24/7 PPI 样本。 */
@Entity(
    tableName = "ppi_247_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class Ppi247SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val ppiMs: Int,
    val errorEstimateMs: Int? = null,
    val triggerType: String? = null,
    val skinContact: String? = null,
    val movement: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
