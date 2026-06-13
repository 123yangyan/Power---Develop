package com.timedrecorder.core.network.dto

import kotlinx.serialization.Serializable

/** 通用 API 响应包装，对应 PRD §14.1 / §14.2 */
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)

/** 文件上传响应 data 字段 */
@Serializable
data class UploadResponseData(
    val fileId: String,
    val status: String,
)

/** 音频处理结果，与 PRD §9.5 JSON 结构一致 */
@Serializable
data class AudioResultDto(
    val fileId: String,
    val fileName: String,
    val status: String,
    val transcript: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val alertFlag: Boolean = false,
    val riskLevel: String? = null,
    val message: String? = null,
    val processedAt: String? = null,
)

/** 批量结果查询请求体 */
@Serializable
data class BatchResultRequest(
    val fileIds: List<String>,
)

/** 批量结果查询响应 data 字段 */
@Serializable
data class BatchResultResponseData(
    val results: List<AudioResultDto> = emptyList(),
)

/** 上传请求元数据（multipart 表单字段） */
data class UploadMetadata(
    val fileName: String,
    val format: String,
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val deviceId: String,
    val localId: String,
)
