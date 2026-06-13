package com.timedrecorder.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.data.scheduler.RecordingScheduler
import com.timedrecorder.core.model.ScheduleTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val recordingScheduler: RecordingScheduler,
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = scheduleRepository.observeAllTasks()
        .map { ScheduleUiState.Success(it) as ScheduleUiState }
        .catch { emit(ScheduleUiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)

    fun toggleEnabled(task: ScheduleTask) {
        viewModelScope.launch {
            scheduleRepository.toggleTaskEnabled(task.id, !task.enabled)
            recordingScheduler.rescheduleAllAlarms()
        }
    }

    fun deleteTask(task: ScheduleTask) {
        viewModelScope.launch {
            scheduleRepository.deleteTask(task)
            recordingScheduler.cancelTaskAlarms(task.id)
            recordingScheduler.rescheduleAllAlarms()
        }
    }
}

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val recordingScheduler: RecordingScheduler,
) : ViewModel() {

    private var taskId: Long = 0

    val uiState = kotlinx.coroutines.flow.MutableStateFlow<TaskEditUiState>(
        TaskEditUiState.Editing(
            task = ScheduleTask(startTimeMinutes = 540, endTimeMinutes = 720),
            isNew = true,
        ),
    )

    fun loadTask(taskId: Long) {
        this.taskId = taskId
        viewModelScope.launch {
            val task = scheduleRepository.getTaskById(taskId)
            if (task != null) {
                uiState.value = TaskEditUiState.Editing(task = task, isNew = false)
            }
        }
    }

    fun updateTaskName(name: String) {
        val current = uiState.value as? TaskEditUiState.Editing ?: return
        uiState.value = current.copy(task = current.task.copy(taskName = name.ifBlank { null }))
    }

    fun updateStartTime(hour: Int, minute: Int) {
        val current = uiState.value as? TaskEditUiState.Editing ?: return
        uiState.value = current.copy(
            task = current.task.copy(startTimeMinutes = hour * 60 + minute),
        )
    }

    fun updateEndTime(hour: Int, minute: Int) {
        val current = uiState.value as? TaskEditUiState.Editing ?: return
        uiState.value = current.copy(
            task = current.task.copy(endTimeMinutes = hour * 60 + minute),
        )
    }

    fun save(onSuccess: () -> Unit) {
        val current = uiState.value as? TaskEditUiState.Editing ?: return
        viewModelScope.launch {
            uiState.value = current.copy(isSaving = true, errorMessage = null)
            val result = scheduleRepository.saveTask(current.task)
            result.fold(
                onSuccess = {
                    recordingScheduler.rescheduleAllAlarms()
                    onSuccess()
                },
                onFailure = { error ->
                    uiState.value = current.copy(
                        isSaving = false,
                        errorMessage = error.message,
                    )
                },
            )
        }
    }
}
