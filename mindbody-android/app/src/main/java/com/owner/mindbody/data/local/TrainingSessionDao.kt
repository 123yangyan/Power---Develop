package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrainingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TrainingSessionEntity>)

    @Query("SELECT * FROM training_sessions WHERE syncState != 'SYNCED' LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<TrainingSessionEntity>

    @Query("UPDATE training_sessions SET syncState = 'SYNCED', remoteId = :remoteId, updatedAt = :updatedAt WHERE devicePath IN (:paths)")
    suspend fun markSynced(paths: List<String>, remoteId: String?, updatedAt: Long = System.currentTimeMillis())

    /** 删除指定日期之前已同步的数据，7天滚动清理专用。安全：只删 SYNCED 行。 */
    @Query("DELETE FROM training_sessions WHERE sessionDate < :cutoffDate AND syncState = 'SYNCED'")
    suspend fun deleteSyncedBeforeDate(cutoffDate: String): Int

    @Query("SELECT devicePath FROM training_sessions")
    suspend fun getAllDevicePaths(): List<String>

    @Query(
        """
        SELECT * FROM training_sessions
        WHERE startTimeMs IS NOT NULL
          AND endTimeMs IS NOT NULL
          AND startTimeMs <= :endMs
          AND endTimeMs >= :startMs
        ORDER BY startTimeMs ASC
        """
    )
    fun observeSessionsBetween(startMs: Long, endMs: Long): Flow<List<TrainingSessionEntity>>

    @Query(
        """
        SELECT * FROM training_sessions
        WHERE (
            startTimeMs IS NOT NULL
            AND endTimeMs IS NOT NULL
            AND startTimeMs <= :endMs
            AND endTimeMs >= :startMs
        )
        OR sessionDate = :sessionDate
        ORDER BY COALESCE(startTimeMs, :dayStartMs) ASC
        """
    )
    fun observeSessionsForDay(
        startMs: Long,
        endMs: Long,
        sessionDate: String,
        dayStartMs: Long
    ): Flow<List<TrainingSessionEntity>>
}
