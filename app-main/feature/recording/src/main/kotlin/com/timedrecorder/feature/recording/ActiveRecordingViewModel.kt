package com.timedrecorder.feature.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.record.RecordingController
import com.timedrecorder.core.data.status.RecordingStateHolder
import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 全屏录音页 UI 状态 */
data class ActiveRecordingUiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val sessionTitle: String = "新录音",
    val elapsedMs: Long = 0L,
    val scenario: RecordingScenario? = null,
    val statusMessage: String = "请说，我在听…",
    val isActiveSession: Boolean = false,
    val showCancelDialog: Boolean = false,
)

@HiltViewModel
class ActiveRecordingViewModel @Inject constructor(
    private val recordingStateHolder: RecordingStateHolder,
    private val recordingController: RecordingController,
) : ViewModel() {

    private val _displayElapsedMs = MutableStateFlow(0L)
    private val _sessionTitle = MutableStateFlow("新录音")
    private val _showCancelDialog = MutableStateFlow(false)

    init {
        // 每秒刷新有效录音时长
        viewModelScope.launch {
            while (isActive) {
                _displayElapsedMs.value = recordingStateHolder.currentDisplayElapsedMs()
                delay(1_000L)
            }
        }
    }

    // combine 最多支持 5 个 Flow，6 个源需拆成嵌套 combine
    val uiState: StateFlow<ActiveRecordingUiState> = combine(
        combine(
            recordingStateHolder.state,
            recordingStateHolder.currentTaskName,
            recordingStateHolder.currentScenario,
        ) { state, taskName, scenario -> Triple(state, taskName, scenario) },
        combine(
            _displayElapsedMs,
            _sessionTitle,
            _showCancelDialog,
        ) { elapsed, title, showCancel -> Triple(elapsed, title, showCancel) },
    ) { taskInfo, localInfo ->
        val (state, taskName, scenario) = taskInfo
        val (elapsed, title, showCancel) = localInfo
        val isActive = taskName != null && state !in setOf(
            RecordingState.IDLE,
            RecordingState.ERROR,
            RecordingState.SCHEDULED,
        )
        ActiveRecordingUiState(
            recordingState = state,
            sessionTitle = title.ifBlank { taskName ?: "新录音" },
            elapsedMs = elapsed,
            scenario = scenario,
            statusMessage = resolveStatusMessage(state),
            isActiveSession = isActive,
            showCancelDialog = showCancel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveRecordingUiState())

    fun updateSessionTitle(title: String) {
        _sessionTitle.value = title
    }

    fun pauseRecording() = recordingController.pauseActiveRecording()

    fun resumeRecording() = recordingController.resumeActiveRecording()

    fun finishRecording() = recordingController.stopActiveRecording()

    fun requestCancel() {
        _showCancelDialog.value = true
    }

    fun dismissCancelDialog() {
        _showCancelDialog.value = false
    }

    fun confirmCancel() {
        _showCancelDialog.value = false
        recordingController.cancelActiveRecording()
    }

    private fun resolveStatusMessage(state: RecordingState): String = when (state) {
        RecordingState.RECORDING -> "请说，我在听…"
        RecordingState.PAUSED -> "已暂停，准备好了点继续"
        RecordingState.SLICING -> "正在切片保存…"
        RecordingState.UPLOADING -> "正在帮你整理，稍等一下"
        RecordingState.ERROR -> "录音出现异常，请检查后重试"
        else -> "准备好了，选一个场景开始吧"
    }
}
