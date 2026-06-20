package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.owner.mindbody.data.sync.SyncableDao
import kotlinx.coroutines.flow.Flow

@Dao
interface SkinTempSampleDao : SyncableDao<SkinTempSampleEntity> {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<SkinTempSampleEntity>)

    @Query(
        """
        SELECT * FROM skin_temp_samples
        WHERE timestamp BETWEEN :startMs AND :endMs
        ORDER BY timestamp ASC
        """
    )
    fun observeBetween(startMs: Long, endMs: Long): Flow<List<SkinTempSampleEntity>>

    @Query(
        """
        SELECT * FROM skin_temp_samples
        WHERE syncState != 'SYNCED'
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    override suspend fun getUnsynced(limit: Int): List<SkinTempSampleEntity>

    @Query(
        """
        UPDATE skin_temp_samples
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
        UPDATE skin_temp_samples
        SET syncState = 'FAILED', updatedAt = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markFailedInternal(ids: List<Long>, updatedAt: Long)

    override suspend fun markFailed(ids: List<Long>) {
        if (ids.isEmpty()) return
        markFailedInternal(ids, System.currentTimeMillis())
    }

    /** 删除指定时间之前已同步的数据，7天滚动清理专用。安全：只删 SYNCED 行。 */
    @Query(
        """
        DELETE FROM skin_temp_samples WHERE id IN (
            SELECT id FROM skin_temp_samples
            WHERE timestamp < :cutoffMs AND syncState = 'SYNCED'
            LIMIT 5000
        )
        """
    )
    suspend fun deleteSyncedBefore(cutoffMs: Long): Int
}
