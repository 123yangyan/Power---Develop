package com.timedrecorder.core.model

/**
 * 录音执行引擎状态，对应 PRD §9.3。
 */
enum class RecordingState {
    /** 空闲 */
    IDLE,
    /** 已排程，等待下一任务 */
    SCHEDULED,
    /** 录音中 */
    RECORDING,
    /** 用户主动暂停 */
    PAUSED,
    /** 切片写入中 */
    SLICING,
    /** 上传中 */
    UPLOADING,
    /** 异常/暂停 */
    ERROR,
    ;

    /** 录音状态中文描述 */
    fun displayName(): String = when (this) {
        IDLE -> "未录音"
        SCHEDULED -> "等待下一任务"
        RECORDING -> "录音中"
        PAUSED -> "已暂停"
        SLICING -> "切片写入中"
        UPLOADING -> "上传中"
        ERROR -> "异常中断"
    }
}
