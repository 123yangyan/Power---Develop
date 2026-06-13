package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActivityDaySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityDaySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ActivityDaySummaryEntity>)

    @Query("SELECT * FROM activity_day_summary WHERE syncState != 'SYNCED' LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<ActivityDaySummaryEntity>

    @Query(
        """
        UPDATE activity_day_summary
        SET syncState = 'SYNCED', remoteId = :remoteId, updatedAt = :updatedAt
        WHERE date IN (:dates)
        """
    )
    suspend fun markSynced(dates: List<String>, remoteId: String?, updatedAt: Long = System.currentTimeMillis())
}
