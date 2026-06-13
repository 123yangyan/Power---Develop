package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NightlyRechargeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NightlyRechargeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NightlyRechargeEntity>)

    @Query("SELECT * FROM nightly_recharge WHERE syncState != 'SYNCED' LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<NightlyRechargeEntity>
}
