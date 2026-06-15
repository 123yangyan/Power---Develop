package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.owner.mindbody.data.sync.SyncableDao
import kotlinx.coroutines.flow.Flow

@Dao
interface PpiSampleDao : SyncableDao<PpiSampleEntity> {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<PpiSampleEntity>)

    @Query(
        """
        SELECT * FROM ppi_samples
        WHERE timestamp BETWEEN :startMs AND :endMs
        ORDER BY timestamp ASC
        """
    )
    fun observeBetween(startMs: Long, endMs: Long): Flow<List<PpiSampleEntity>>

    @Query(
        """
        SELECT * FROM ppi_samples
        WHERE syncState != 'SYNCED'
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    override suspend fun getUnsynced(limit: Int): List<PpiSampleEntity>

    @Query(
        """
        UPDATE ppi_samples
        SET syncState = 'SYNCED', remoteId = :remoteId, updatedAt = :updatedAt
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
        UPDATE ppi_samples
        SET syncState = 'FAILED', updatedAt = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markFailedInternal(ids: List<Long>, updatedAt: Long)

    override suspend fun markFailed(ids: List<Long>) {
        if (ids.isEmpty()) return
        markFailedInternal(ids, System.currentTimeMillis())
    }
}
