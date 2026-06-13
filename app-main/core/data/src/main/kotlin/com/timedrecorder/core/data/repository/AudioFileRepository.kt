package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import kotlinx.coroutines.flow.Flow

/** 本地录音文件 Repository 接口 */
interface AudioFileRepository {
    fun observeAllFiles(): Flow<List<AudioFile>>
    fun observeRecentFiles(limit: Int = 10): Flow<List<AudioFile>>
    fun observePendingUploads(): Flow<List<AudioFile>>
    fun observeByUploadStatus(status: UploadStatus): Flow<List<AudioFile>>
    suspend fun getFileById(id: Long): AudioFile?
    suspend fun insertFile(file: AudioFile): Long
    suspend fun updateFile(file: AudioFile)
    suspend fun deleteFile(file: AudioFile)
    suspend fun updateUploadStatus(id: Long, status: UploadStatus)
    suspend fun updateProcessStatus(id: Long, status: ProcessStatus)
    suspend fun getExpiredFiles(beforeMillis: Long): List<AudioFile>
    /** 已上传但处理结果未完成的文件（用于启动补偿轮询） */
    suspend fun getUploadedAwaitingResult(): List<AudioFile>
    /** 手动重传前重置单文件上传状态 */
    suspend fun resetUploadForRetry(id: Long)
    /** 批量重传前重置所有未成功上传的文件 */
    suspend fun resetAllPendingUploadsForRetry()
    /** 删除本地录音文件及关联数据库记录（摘要、消息） */
    suspend fun deleteRecordingCompletely(fileId: Long): Result<Unit>
}
