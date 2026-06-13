package com.timedrecorder.core.model

/**
 * 云端处理结果，对应 PRD §13.3 process_result。
 */
data class ProcessResult(
    val id: Long = 0,
    val fileId: Long,
    val transcript: String? = null,
    val title: String? = null,
    val summary: String?,
    val keywords: List<String>,
    val alertFlag: Boolean,
    val riskLevel: RiskLevel?,
    val resultJson: String?,
    val processedAt: Long?,
    val createdAt: Long = System.currentTimeMillis(),
)
