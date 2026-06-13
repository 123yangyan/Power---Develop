package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.owner.mindbody.data.sync.SyncableDao
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao : SyncableDao<MoodEntryEntity> {

    @Insert
    suspend fun insert(entry: MoodEntryEntity): Long

    @Update
    suspend fun update(entry: MoodEntryEntity)

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM mood_entries ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries ORDER BY occurredAt DESC")
    suspend fun getAll(): List<MoodEntryEntity>

    @Query("SELECT * FROM mood_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MoodEntryEntity?

    @Query(
        """
        SELECT * FROM mood_entries
        WHERE syncState != 'SYNCED'
        ORDER BY occurredAt ASC
        LIMIT :limit
        """
    )
    override suspend fun getUnsynced(limit: Int): List<MoodEntryEntity>

    @Query(
        """
        UPDATE mood_entries
        SET syncState = 'SYNCED',
            remoteId = :remoteId,
            updatedAt = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markSyncedInternal(ids: List<Long>, remoteId: String?, updatedAt: Long)

    override suspend fun markSynced(ids: List<Long>, remoteId: String?) {
        if (ids.isEmpty()) return
        markSyncedInternal(ids, remoteId, System.currentTimeMillis())
    }

    @Query(
        """
        UPDATE mood_entries
        SET syncState = 'FAILED',
            updatedAt = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markFailedInternal(ids: List<Long>, updatedAt: Long)

    override suspend fun markFailed(ids: List<Long>) {
        if (ids.isEmpty()) return
        markFailedInternal(ids, System.currentTimeMillis())
    }
}
