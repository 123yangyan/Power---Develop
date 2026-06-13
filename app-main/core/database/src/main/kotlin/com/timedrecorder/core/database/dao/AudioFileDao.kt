package com.timedrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timedrecorder.core.database.entity.AudioFileEntity
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import kotlinx.coroutines.flow.Flow

/** 本地录音文件 DAO */
@Dao
interface AudioFileDao {
    @Query("SELECT * FROM audio_file ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AudioFileEntity>>

    @Query("SELECT * FROM audio_file WHERE id = :id")
    suspend fun getById(id: Long): AudioFileEntity?

    @Query("SELECT * FROM audio_file WHERE upload_status = :status ORDER BY created_at ASC")
    fun observeByUploadStatus(status: UploadStatus): Flow<List<AudioFileEntity>>

    @Query(
        """
        SELECT * FROM audio_file
        WHERE upload_status IN ('PENDING', 'FAILED', 'RETRYING', 'UPLOADING')
        ORDER BY created_at ASC
        """,
    )
    fun observePendingUploads(): Flow<List<AudioFileEntity>>

    @Query("SELECT * FROM audio_file ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AudioFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AudioFileEntity): Long

    @Update
    suspend fun update(entity: AudioFileEntity)

    @Delete
    suspend fun delete(entity: AudioFileEntity)

    @Query("UPDATE audio_file SET upload_status = :status WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: UploadStatus)

    @Query(
        """
        UPDATE audio_file
        SET upload_status = :status, upload_retry_count = :retryCount, server_file_id = :serverFileId
        WHERE id = :id
        """,
    )
    suspend fun updateUploadResult(
        id: Long,
        status: UploadStatus,
        retryCount: Int,
        serverFileId: String?,
    )

    @Query("UPDATE audio_file SET process_status = :status WHERE id = :id")
    suspend fun updateProcessStatus(id: Long, status: ProcessStatus)

    @Query("SELECT * FROM audio_file WHERE created_at < :beforeMillis")
    suspend fun getExpiredFiles(beforeMillis: Long): List<AudioFileEntity>

    /** 已上传成功但云端处理结果尚未拉取完成的文件 */
    @Query(
        """
        SELECT * FROM audio_file
        WHERE upload_status = 'SUCCESS'
          AND process_status != 'COMPLETED'
          AND server_file_id IS NOT NULL
        ORDER BY created_at ASC
        """,
    )
    suspend fun getUploadedAwaitingResult(): List<AudioFileEntity>

    /** 手动重传前：重置单文件上传状态与重试计数（已成功上传的不动） */
    @Query(
        """
        UPDATE audio_file
        SET upload_status = 'PENDING', upload_retry_count = 0
        WHERE id = :id AND upload_status != 'SUCCESS'
        """,
    )
    suspend fun resetUploadForRetry(id: Long)

    /** 批量重传前：重置所有未成功上传的文件 */
    @Query(
        """
        UPDATE audio_file
        SET upload_status = 'PENDING', upload_retry_count = 0
        WHERE upload_status IN ('PENDING', 'FAILED', 'RETRYING', 'UPLOADING')
        """,
    )
    suspend fun resetAllPendingUploadsForRetry()
}
