package com.timedrecorder.core.data.status

import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 录音引擎全局状态持有者，供 UI 与诊断页读取。
 * 有效录音时长由 Service 在 RECORDING 段内累计，不含暂停与切片间隙。
 */
@Singleton
class RecordingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _currentTaskId = MutableStateFlow<Long?>(null)
    val currentTaskId: StateFlow<Long?> = _currentTaskId.asStateFlow()

    private val _currentTaskName = MutableStateFlow<String?>(null)
    val currentTaskName: StateFlow<String?> = _currentTaskName.asStateFlow()

    /** 已结束录音段的累计毫秒数（不含当前进行中的段） */
    private val _elapsedRecordingMs = MutableStateFlow(0L)
    val elapsedRecordingMs: StateFlow<Long> = _elapsedRecordingMs.asStateFlow()

    /** 当前 RECORDING 段的起始时间，null 表示未在累计 */
    private val _activeSegmentStartMs = MutableStateFlow<Long?>(null)
    val activeSegmentStartMs: StateFlow<Long?> = _activeSegmentStartMs.asStateFlow()

    /** 当前手动录音场景，供 UI 展示时长提示与标题 */
    private val _currentScenario = MutableStateFlow<RecordingScenario?>(null)
    val currentScenario: StateFlow<RecordingScenario?> = _currentScenario.asStateFlow()

    val isPaused: Boolean
        get() = _state.value == RecordingState.PAUSED

    fun updateState(state: RecordingState) {
        _state.value = state
    }

    fun setCurrentTask(taskId: Long?, taskName: String?) {
        _currentTaskId.value = taskId
        _currentTaskName.value = taskName
    }

    /** 新会话开始时清零计时 */
    fun beginSession(taskId: Long, taskName: String, scenario: RecordingScenario? = null) {
        _elapsedRecordingMs.value = 0L
        _activeSegmentStartMs.value = null
        _currentScenario.value = scenario
        setCurrentTask(taskId, taskName)
    }

    /** MediaRecorder 开始录音时调用，开始累计有效时长 */
    fun beginRecordingSegment() {
        _activeSegmentStartMs.value = System.currentTimeMillis()
    }

    /** 离开 RECORDING 状态时调用，将当前段计入累计 */
    fun endRecordingSegment() {
        val start = _activeSegmentStartMs.value ?: return
        _elapsedRecordingMs.value += System.currentTimeMillis() - start
        _activeSegmentStartMs.value = null
    }

    /** UI 展示用：累计 + 当前段（若正在录音） */
    fun currentDisplayElapsedMs(): Long {
        val start = _activeSegmentStartMs.value ?: return _elapsedRecordingMs.value
        return _elapsedRecordingMs.value + (System.currentTimeMillis() - start)
    }

    fun reset() {
        _state.value = RecordingState.IDLE
        _currentTaskId.value = null
        _currentTaskName.value = null
        _currentScenario.value = null
        _elapsedRecordingMs.value = 0L
        _activeSegmentStartMs.value = null
    }
}
