package com.timedrecorder.core.data.record

import com.timedrecorder.core.model.RecordingScenario

/**
 * 录音控制接口，由 sync 模块的 RecordServiceController 实现。
 * feature/home 通过此接口控制手动与计划任务的录音会话。
 */
interface RecordingController {
    /** 开始手动录音（taskId = 0），可携带场景元数据 */
    fun startManualRecording(scenario: RecordingScenario = RecordingScenario.QUICK_NOTE)

    /** 暂停当前活跃会话（手动或计划） */
    fun pauseActiveRecording()

    /** 继续当前活跃会话 */
    fun resumeActiveRecording()

    /** 停止当前活跃会话并保存当前切片 */
    fun stopActiveRecording()

    /** 放弃当前录音，不保存切片 */
    fun cancelActiveRecording()
}
