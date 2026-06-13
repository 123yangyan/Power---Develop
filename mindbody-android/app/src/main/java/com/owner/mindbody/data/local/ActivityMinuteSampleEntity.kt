package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 活动分钟级样本（步数、MET、活动等级）。 */
@Entity(
    tableName = "activity_minute_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class ActivityMinuteSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val steps: Int? = null,
    val metX100: Int? = null,
    val activityLevel: Int? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta(createdAt = timestamp, updatedAt = timestamp)
)
