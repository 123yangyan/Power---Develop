package com.timedrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timedrecorder.core.database.entity.ProcessResultEntity
import kotlinx.coroutines.flow.Flow

/** 云端处理结果 DAO */
@Dao
interface ProcessResultDao {
    @Query("SELECT * FROM process_result ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ProcessResultEntity>>

    @Query(
        """
        SELECT * FROM process_result
        WHERE file_id = :fileId
        ORDER BY processed_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getByFileId(fileId: Long): ProcessResultEntity?

    @Query(
        """
        SELECT * FROM process_result
        WHERE file_id = :fileId
        ORDER BY processed_at DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeByFileId(fileId: Long): Flow<ProcessResultEntity?>

    @Query("SELECT * FROM process_result ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProcessResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProcessResultEntity): Long

    @Query("SELECT * FROM process_result WHERE file_id IN (:fileIds)")
    suspend fun getByFileIds(fileIds: List<Long>): List<ProcessResultEntity>

    @Query("DELETE FROM process_result WHERE file_id = :fileId")
    suspend fun deleteByFileId(fileId: Long)
}
