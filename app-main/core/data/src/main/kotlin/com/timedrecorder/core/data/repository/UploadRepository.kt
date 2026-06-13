package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.UploadStatus

/** 文件上传 Repository 接口 */
interface UploadRepository {
    suspend fun uploadFile(fileId: Long): Result<String>
    suspend fun retryUpload(fileId: Long): Result<String>
    suspend fun markUploadFailed(fileId: Long, error: String)
}

/** 上传结果 */
data class UploadOutcome(
    val serverFileId: String,
    val localFileId: Long,
)
