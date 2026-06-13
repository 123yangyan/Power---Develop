package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/** 每日活动汇总（步数、活跃时间、卡路里）。 */
@Entity(
    tableName = "activity_day_summary",
    indices = [Index(value = ["syncState"])]
)
data class ActivityDaySummaryEntity(
    @androidx.room.PrimaryKey
    val date: String,
    val steps: Int? = null,
    val activeTimeMinutes: Int? = null,
    val caloriesTotal: Int? = null,
    val caloriesActivity: Int? = null,
    val caloriesTraining: Int? = null,
    val caloriesBmr: Int? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)
