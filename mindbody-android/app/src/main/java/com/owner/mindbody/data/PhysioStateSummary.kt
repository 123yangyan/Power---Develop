package com.owner.mindbody.data

/**
 * 聚合的生理状态快照，由 AppStorage.latestPhysioState() 返回。
 * 短期策略：PhysioStateViewModel 通过 API 轮询（30s）更新此对象，
 * 不依赖新 Room Entity，不破坏 P0 存储架构红线。
 */
data class PhysioStateSummary(
    /** 状态分类标签：baseline_building / calm / normal / elevated / anxious / high_anxiety */
    val stateLabel: String,
    /** 焦虑评分 0–100 */
    val anxietyScore: Float,
    /** HRV 时域 */
    val rmssd: Float? = null,
    val sdnn: Float? = null,
    /** HRV 频域 */
    val lfHf: Float? = null,
    /** 呼吸频率（次/分） */
    val breathingRate: Float? = null,
    /** 样本熵 */
    val sampEn: Float? = null,
    /** 分形维数 */
    val dfaAlpha1: Float? = null,
    /** 是否检测到心率突升 */
    val hrSurgeFlag: Boolean = false,
    /** 当前分析窗口 ID */
    val windowId: Long? = null,
    /** 数据时间戳（ms） */
    val timestampMs: Long = 0L,
    /** LLM 最新反馈文字 */
    val llmMessage: String? = null,
    /** EWMA 基线窗口数（目标 50） */
    val baselineWindowCount: Int = 0,
    /** 最近一次推流时间戳 */
    val lastStreamTs: Long? = null
)

/**
 * LLM 反馈历史条目，用于 FeedbackHistoryListScreen。
 */
data class LlmFeedbackEntry(
    val id: Long,
    val timestampMs: Long,
    val stateLabel: String,
    val anxietyScore: Float,
    val message: String,
    val tone: String = "",
    val userResponse: String? = null
)
