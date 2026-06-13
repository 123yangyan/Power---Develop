package com.timedrecorder.core.model

/**
 * 云端处理状态，对应 PRD §9.5。
 */
enum class ProcessStatus {
    /** 待处理 */
    PENDING,
    /** 处理中 */
    PROCESSING,
    /** 已完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
}
