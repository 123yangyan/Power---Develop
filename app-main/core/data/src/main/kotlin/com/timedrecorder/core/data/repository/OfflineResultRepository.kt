package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.data.util.AlertEvaluator
import com.timedrecorder.core.database.dao.AudioFileDao
import com.timedrecorder.core.database.dao.ProcessResultDao
import com.timedrecorder.core.database.mapper.asEntity
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.ProcessResult
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.RiskLevel
import com.timedrecorder.core.network.AudioApiProvider
import com.timedrecorder.core.network.dto.BatchResultRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineResultRepository @Inject constructor(
    private val processResultDao: ProcessResultDao,
    private val audioFileDao: AudioFileDao,
    private val audioApiProvider: AudioApiProvider,
    private val messageRepository: MessageRepository,
    private val logRepository: LogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ResultRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAllResults(): Flow<List<ProcessResult>> =
        processResultDao.observeAll().map { list -> list.map { it.asExternalModel() } }

    override fun observeRecentResults(limit: Int): Flow<List<ProcessResult>> =
        processResultDao.observeRecent(limit).map { list -> list.map { it.asExternalModel() } }

    override fun observeResultByFileId(fileId: Long): Flow<ProcessResult?> =
        processResultDao.observeByFileId(fileId).map { entity -> entity?.asExternalModel() }

    override suspend fun getResultByFileId(fileId: Long): ProcessResult? = withContext(ioDispatcher) {
        processResultDao.getByFileId(fileId)?.asExternalModel()
    }

    override suspend fun pollResult(serverFileId: String, localFileId: Long): Result<ProcessResult> =
        withContext(ioDispatcher) {
            runCatching {
                val api = audioApiProvider.getApiService()
                val response = api.getResult(serverFileId)
                val dto = response.data
                if (response.code != 0 || dto == null) {
                    throw IllegalStateException(response.message)
                }
                saveResultFromDto(dto, localFileId)
            }.onFailure {
                logRepository.log(LogType.NETWORK, LogLevel.WARN, "轮询结果失败: ${it.message}")
            }
        }

    override suspend fun pollResultsBatch(items: List<Pair<String, Long>>): List<Result<ProcessResult>> =
        withContext(ioDispatcher) {
            if (items.isEmpty()) return@withContext emptyList()
            runCatching {
                val api = audioApiProvider.getApiService()
                val response = api.batchResult(BatchResultRequest(items.map { it.first }))
                val batchData = response.data
                if (response.code != 0 || batchData == null) {
                    throw IllegalStateException(response.message)
                }
                batchData.results.map { dto ->
                    val localId = items.first { it.first == dto.fileId }.second
                    runCatching { saveResultFromDto(dto, localId) }
                }
            }.getOrElse { error ->
                items.map { Result.failure<ProcessResult>(error) }
            }
        }

    /** 将 API 响应转为本地 ProcessResult 并持久化 */
    private suspend fun saveResultFromDto(
        dto: com.timedrecorder.core.network.dto.AudioResultDto,
        localFileId: Long,
    ): ProcessResult {
        val previousStatus = audioFileDao.getById(localFileId)?.processStatus

        val processStatus = when (dto.status.lowercase()) {
            "completed" -> ProcessStatus.COMPLETED
            "processing" -> ProcessStatus.PROCESSING
            "failed" -> ProcessStatus.FAILED
            else -> ProcessStatus.PENDING
        }

        audioFileDao.updateProcessStatus(localFileId, processStatus)

        val processedAt = dto.processedAt?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }

        // 保留首次写入时间，配合 file_id 唯一索引实现 upsert
        val existing = processResultDao.getByFileId(localFileId)
        val result = ProcessResult(
            id = existing?.id ?: 0,
            fileId = localFileId,
            transcript = dto.transcript,
            title = dto.title,
            summary = dto.summary,
            keywords = dto.keywords,
            alertFlag = dto.alertFlag,
            riskLevel = RiskLevel.fromApiValue(dto.riskLevel),
            resultJson = json.encodeToString(dto),
            processedAt = processedAt,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )

        processResultDao.insert(result.asEntity())

        // 仅在首次完成时写入告警，避免轮询重复通知
        if (
            processStatus == ProcessStatus.COMPLETED &&
            previousStatus != ProcessStatus.COMPLETED &&
            AlertEvaluator.shouldNotify(dto)
        ) {
            messageRepository.createAlertMessage(
                title = "录音异常提醒",
                content = dto.message ?: "检测到关键词：${dto.keywords.joinToString("、")}",
                fileId = localFileId,
            )
        }

        logRepository.log(LogType.NETWORK, LogLevel.INFO, "结果已保存: ${dto.fileName}")
        return result
    }
}
