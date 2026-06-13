package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/** 训练/运动会话记录。 */
@Entity(
    tableName = "training_sessions",
    indices = [Index(value = ["syncState"])]
)
data class TrainingSessionEntity(
    @androidx.room.PrimaryKey
    val devicePath: String,
    val sessionDate: String,
    val fileSizeBytes: Long = 0L,
    val exerciseCount: Int = 0,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val durationSeconds: Int? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)
