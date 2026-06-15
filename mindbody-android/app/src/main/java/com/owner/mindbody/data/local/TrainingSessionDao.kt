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
}
