package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 在线 PPI（心跳间期）流样本。 */
@Entity(
    tableName = "ppi_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"]),
        Index(value = ["timestamp", "syncState"])
    ]
)
data class PpiSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val ppiMs: Int,
    val errorEstimateMs: Int? = null,
    val hrBpm: Int? = null,
    val blockerBit: Boolean = false,
    val skinContactSupported: Boolean = true,
    val skinContactStatus: Boolean = true,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
