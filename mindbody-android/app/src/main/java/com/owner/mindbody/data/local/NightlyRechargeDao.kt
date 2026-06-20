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

    @Query("UPDATE nightly_recharge SET syncState = 'SYNCED', remoteId = :remoteId, updatedAt = :updatedAt WHERE date IN (:dates)")
    suspend fun markSynced(dates: List<String>, remoteId: String?, updatedAt: Long = System.currentTimeMillis())

    /** 删除指定日期之前已同步的数据，7天滚动清理专用。安全：只删 SYNCED 行。 */
    @Query("DELETE FROM nightly_recharge WHERE date < :cutoffDate AND syncState = 'SYNCED'")
    suspend fun deleteSyncedBeforeDate(cutoffDate: String): Int
}
