package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.AppConstants
import com.timedrecorder.core.common.DeviceIdProvider
import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.database.dao.AudioFileDao
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.UploadStatus
import com.timedrecorder.core.network.AudioApiProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineUploadRepository @Inject constructor(
    private val audioFileDao: AudioFileDao,
    private val audioApiProvider: AudioApiProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val logRepository: LogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UploadRepository {

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())

    override suspend fun uploadFile(fileId: Long): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val entity = audioFileDao.getById(fileId)
                ?: throw IllegalArgumentException("文件记录不存在: $fileId")
            val file = entity.asExternalModel()
            val physicalFile = File(file.filePath)
            if (!physicalFile.exists()) {
                throw IllegalStateException("本地文件不存在: ${file.filePath}")
            }

            // 标记上传中
            audioFileDao.updateUploadStatus(fileId, UploadStatus.UPLOADING)

            val deviceId = deviceIdProvider.getDeviceId()
            val api = audioApiProvider.getApiService()

            val filePart = MultipartBody.Part.createFormData(
                "file",
                file.fileName,
                physicalFile.asRequestBody(file.format.mimeType.toMediaType()),
            )

            val startTime = isoFormatter.format(Instant.ofEpochMilli(file.startAt))
            val endTime = file.endAt?.let { isoFormatter.format(Instant.ofEpochMilli(it)) }
                ?: startTime

            val response = api.uploadAudio(
                file = filePart,
                fileName = file.fileName.toRequestBody(),
                format = file.format.extension.toRequestBody(),
                startTime = startTime.toRequestBody(),
                endTime = endTime.toRequestBody(),
                duration = file.duration.toString().toRequestBody(),
                deviceId = deviceId.toRequestBody(),
                localId = fileId.toString().toRequestBody(),
            )

            val uploadData = response.data
            if (response.code != 0 || uploadData == null) {
                throw IllegalStateException(response.message)
            }

            val serverFileId = uploadData.fileId
            audioFileDao.updateUploadResult(
                id = fileId,
                status = UploadStatus.SUCCESS,
                retryCount = file.uploadRetryCount,
                serverFileId = serverFileId,
            )

            logRepository.log(LogType.UPLOAD, LogLevel.INFO, "上传成功: ${file.fileName} -> $serverFileId")
            serverFileId
        }.onFailure { error ->
            handleUploadFailure(fileId, error)
        }
    }

    override suspend fun retryUpload(fileId: Long): Result<String> = withContext(ioDispatcher) {
        val entity = audioFileDao.getById(fileId) ?: return@withContext Result.failure(
            IllegalArgumentException("文件记录不存在"),
        )
        // 重置重试状态
        audioFileDao.updateUploadResult(
            id = fileId,
            status = UploadStatus.RETRYING,
            retryCount = 0,
            serverFileId = entity.serverFileId,
        )
        uploadFile(fileId)
    }

    override suspend fun markUploadFailed(fileId: Long, error: String) = withContext(ioDispatcher) {
        handleUploadFailure(fileId, Exception(error))
    }

    private suspend fun handleUploadFailure(fileId: Long, error: Throwable) {
        val entity = audioFileDao.getById(fileId) ?: return
        val newRetryCount = entity.uploadRetryCount + 1
        val status = if (newRetryCount >= AppConstants.MAX_UPLOAD_RETRY_COUNT) {
            UploadStatus.FAILED
        } else {
            UploadStatus.PENDING
        }
        audioFileDao.updateUploadResult(
            id = fileId,
            status = status,
            retryCount = newRetryCount,
            serverFileId = entity.serverFileId,
        )
        logRepository.log(LogType.UPLOAD, LogLevel.ERROR, "上传失败($newRetryCount): ${error.message}")
    }
}
