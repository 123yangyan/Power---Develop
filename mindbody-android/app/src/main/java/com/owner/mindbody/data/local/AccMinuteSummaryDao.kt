package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.owner.mindbody.data.sync.SyncableDao

@Dao
interface AccMinuteSummaryDao : SyncableDao<AccMinuteSummaryEntity> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<AccMinuteSummaryEntity>)

    @Query(
        """
        SELECT * FROM acc_minute_summary
        WHERE syncState != 'SYNCED'
        ORDER BY minuteTimestamp ASC
        LIMIT :limit
        """
    )
    override suspend fun getUnsynced(limit: Int): List<AccMinuteSummaryEntity>

    @Query(
        """
        UPDATE acc_minute_summary
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
        UPDATE acc_minute_summary
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
        DELETE FROM acc_minute_summary WHERE id IN (
            SELECT id FROM acc_minute_summary
            WHERE minuteTimestamp < :cutoffMs AND syncState = 'SYNCED'
            LIMIT 5000
        )
        """
    )
    suspend fun deleteSyncedBefore(cutoffMs: Long): Int
}
