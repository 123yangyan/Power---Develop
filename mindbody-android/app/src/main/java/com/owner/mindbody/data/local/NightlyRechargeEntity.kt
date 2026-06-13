package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/** 夜间恢复（Nightly Recharge）每日一条。 */
@Entity(
    tableName = "nightly_recharge",
    indices = [Index(value = ["syncState"])]
)
data class NightlyRechargeEntity(
    @androidx.room.PrimaryKey
    val date: String,
    val ansChargePercent: Int? = null,
    val recoveryIndicator: Int? = null,
    val ansRate: Int? = null,
    val hrMeanBpm: Int? = null,
    val hrMinBpm: Int? = null,
    val rrMeanMs: Int? = null,
    val breathingRateHz: Float? = null,
    val sleepTip: String? = null,
    val vitalityTip: String? = null,
    val exerciseTip: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)
