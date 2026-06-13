package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.database.dao.AudioFileDao
import com.timedrecorder.core.database.dao.MessageDao
import com.timedrecorder.core.database.dao.ProcessResultDao
import com.timedrecorder.core.database.mapper.asEntity
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAudioFileRepository @Inject constructor(
    private val audioFileDao: AudioFileDao,
    private val processResultDao: ProcessResultDao,
    private val messageDao: MessageDao,
    private val logRepository: LogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioFileRepository {

    override fun observeAllFiles(): Flow<List<AudioFile>> =
        audioFileDao.observeAll().map { list -> list.map { it.asExternalModel() } }

    override fun observeRecentFiles(limit: Int): Flow<List<AudioFile>> =
        audioFileDao.observeRecent(limit).map { list -> list.map { it.asExternalModel() } }

    override fun observePendingUploads(): Flow<List<AudioFile>> =
        audioFileDao.observePendingUploads().map { list -> list.map { it.asExternalModel() } }

    override fun observeByUploadStatus(status: UploadStatus): Flow<List<AudioFile>> =
        audioFileDao.observeByUploadStatus(status).map { list -> list.map { it.asExternalModel() } }

    override suspend fun getFileById(id: Long): AudioFile? = withContext(ioDispatcher) {
        audioFileDao.getById(id)?.asExternalModel()
    }

    override suspend fun insertFile(file: AudioFile): Long = withContext(ioDispatcher) {
        audioFileDao.insert(file.asEntity())
    }

    override suspend fun updateFile(file: AudioFile) = withContext(ioDispatcher) {
        audioFileDao.update(file.asEntity())
    }

    override suspend fun deleteFile(file: AudioFile) = withContext(ioDispatcher) {
        audioFileDao.delete(file.asEntity())
    }

    override suspend fun updateUploadStatus(id: Long, status: UploadStatus) = withContext(ioDispatcher) {
        audioFileDao.updateUploadStatus(id, status)
    }

    override suspend fun updateProcessStatus(id: Long, status: ProcessStatus) = withContext(ioDispatcher) {
        audioFileDao.updateProcessStatus(id, status)
    }

    override suspend fun getExpiredFiles(beforeMillis: Long): List<AudioFile> = withContext(ioDispatcher) {
        audioFileDao.getExpiredFiles(beforeMillis).map { it.asExternalModel() }
    }

    override suspend fun getUploadedAwaitingResult(): List<AudioFile> = withContext(ioDispatcher) {
        audioFileDao.getUploadedAwaitingResult().map { it.asExternalModel() }
    }

    override suspend fun resetUploadForRetry(id: Long) = withContext(ioDispatcher) {
        audioFileDao.resetUploadForRetry(id)
    }

    override suspend fun resetAllPendingUploadsForRetry() = withContext(ioDispatcher) {
        audioFileDao.resetAllPendingUploadsForRetry()
    }

    override suspend fun deleteRecordingCompletely(fileId: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val entity = audioFileDao.getById(fileId)
                ?: throw IllegalArgumentException("文件记录不存在")
            val file = entity.asExternalModel()
            val physicalFile = File(file.filePath)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
            processResultDao.deleteByFileId(fileId)
            messageDao.deleteByFileId(fileId)
            audioFileDao.delete(entity)
            logRepository.log(LogType.SYSTEM, LogLevel.INFO, "已删除录音: ${file.fileName}")
        }
    }
}
