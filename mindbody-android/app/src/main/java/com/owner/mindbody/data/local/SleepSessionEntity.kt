package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/** 睡眠会话（阶段、时长等）。 */
@Entity(
    tableName = "sleep_sessions",
    indices = [Index(value = ["syncState"])]
)
data class SleepSessionEntity(
    @androidx.room.PrimaryKey
    val date: String,
    val sleepStartTimeMs: Long? = null,
    val sleepEndTimeMs: Long? = null,
    val sleepGoalMinutes: Int? = null,
    val userSleepRating: Int? = null,
    val batteryRanOut: Boolean = false,
    val sleepSkinTempCelsius: Float? = null,
    val sleepSkinTempDeviation: Float? = null,
    val sleepWakePhasesJson: String? = null,
    val sleepCyclesJson: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)
