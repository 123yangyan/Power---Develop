package com.timedrecorder.feature.schedule

import com.timedrecorder.core.model.ScheduleTask

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(val tasks: List<ScheduleTask>) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

sealed interface TaskEditUiState {
    data class Editing(
        val task: ScheduleTask,
        val isNew: Boolean,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
    ) : TaskEditUiState
}
