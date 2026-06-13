package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.ProcessResult
import kotlinx.coroutines.flow.Flow

/** 云端处理结果 Repository 接口 */
interface ResultRepository {
    fun observeAllResults(): Flow<List<ProcessResult>>
    fun observeRecentResults(limit: Int = 10): Flow<List<ProcessResult>>
    fun observeResultByFileId(fileId: Long): Flow<ProcessResult?>
    suspend fun getResultByFileId(fileId: Long): ProcessResult?
    suspend fun pollResult(serverFileId: String, localFileId: Long): Result<ProcessResult>
    suspend fun pollResultsBatch(items: List<Pair<String, Long>>): List<Result<ProcessResult>>
}
