package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.owner.mindbody.data.sync.SyncableDao
import kotlinx.coroutines.flow.Flow

@Dao
interface HrSampleDao : SyncableDao<HrSampleEntity> {

    @Insert
    suspend fun insert(sample: HrSampleEntity): Long

    @Insert
    suspend fun insertAll(samples: List<HrSampleEntity>)

    /** 获取指定时间范围内的样本，按时间升序 */
    @Query(
        """
        SELECT * FROM hr_samples
        WHERE timestamp >= :startMs AND timestamp <= :endMs
        ORDER BY timestamp ASC
        """
    )
    fun observeBetween(startMs: Long, endMs: Long): Flow<List<HrSampleEntity>>

    @Query(
        """
        SELECT * FROM hr_samples
        WHERE timestamp >= :startMs AND timestamp <= :endMs
        ORDER BY timestamp ASC
        """
    )
    suspend fun getBetween(startMs: Long, endMs: Long): List<HrSampleEntity>

    /** 分页读取历史心率，避免大量数据一次性进入内存。 */
    @Query(
        """
        SELECT * FROM hr_samples
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPage(limit: Int, offset: Int): List<HrSampleEntity>

    @Query("SELECT COUNT(*) FROM hr_samples WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun countBetween(startMs: Long, endMs: Long): Int

    @Query("SELECT AVG(bpm) FROM hr_samples WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun averageBetween(startMs: Long, endMs: Long): Double?

    @Query("SELECT MIN(bpm) FROM hr_samples WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun minBetween(startMs: Long, endMs: Long): Int?

    @Query("SELECT MAX(bpm) FROM hr_samples WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun maxBetween(startMs: Long, endMs: Long): Int?

    /** 删除 24 小时以前的原始样本，控制本地存储 */
    @Query("DELETE FROM hr_samples WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    @Query(
        """
        SELECT * FROM hr_samples
        WHERE syncState != 'SYNCED'
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    override suspend fun getUnsynced(limit: Int): List<HrSampleEntity>

    @Query(
        """
        UPDATE hr_samples
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
        UPDATE hr_samples
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
