package com.timedrecorder.feature.home

import com.timedrecorder.core.model.RecordingState
import com.timedrecorder.core.model.ScheduleTask
import com.timedrecorder.core.model.TimelineItem

/** 首页 UI 状态 — 效率大盘：搜索 + 场景横滑 + 时间线内容流 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val recordingState: RecordingState,
        val currentTaskName: String?,
        val todaySchedules: List<ScheduleTask>,
        val timelineItems: List<TimelineItem>,
        val searchQuery: String = "",
        val isActiveSession: Boolean = false,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
