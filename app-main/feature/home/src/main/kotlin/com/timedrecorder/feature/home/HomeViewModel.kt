package com.timedrecorder.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.record.RecordingController
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.data.repository.UploadRepository
import com.timedrecorder.core.data.scheduler.UploadScheduler
import com.timedrecorder.core.data.status.RecordingStateHolder
import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.RecordingState
import com.timedrecorder.core.model.TimelineItem
import com.timedrecorder.core.model.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    private val audioFileRepository: AudioFileRepository,
    resultRepository: ResultRepository,
    private val recordingStateHolder: RecordingStateHolder,
    private val recordingController: RecordingController,
    private val uploadRepository: UploadRepository,
    private val uploadScheduler: UploadScheduler,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            recordingStateHolder.state,
            recordingStateHolder.currentTaskName,
            scheduleRepository.observeEnabledTasks(),
        ) { state, taskName, schedules -> Triple(state, taskName, schedules) },
        combine(
            audioFileRepository.observeRecentFiles(10),
            resultRepository.observeRecentResults(10),
        ) { uploads, results -> Pair(uploads, results) },
        _searchQuery,
    ) { taskInfo, recentInfo, query ->
        val state = taskInfo.first
        val timeline = buildTimeline(
            uploads = recentInfo.first,
            results = recentInfo.second,
            query = query,
        )
        HomeUiState.Success(
            recordingState = state,
            currentTaskName = taskInfo.second,
            todaySchedules = taskInfo.third,
            timelineItems = timeline,
            searchQuery = query,
            isActiveSession = isActiveRecordingSession(state, taskInfo.second),
        ) as HomeUiState
    }.catch { emit(HomeUiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startManualRecording(scenario: RecordingScenario) {
        recordingController.startManualRecording(scenario)
    }

    /** 重新上传失败的录音文件 */
    fun retryUpload(fileId: Long) {
        viewModelScope.launch {
            val file = audioFileRepository.getFileById(fileId) ?: return@launch
            if (file.uploadStatus == UploadStatus.SUCCESS) return@launch
            audioFileRepository.resetUploadForRetry(fileId)
            uploadRepository.uploadFile(fileId)
                .onSuccess { serverFileId ->
                    uploadScheduler.enqueuePoll(serverFileId, fileId)
                }
        }
    }

    /** 删除笔记及关联录音、摘要 */
    fun deleteNote(fileId: Long) {
        viewModelScope.launch {
            uploadScheduler.cancelFileWork(fileId)
            audioFileRepository.deleteRecordingCompletely(fileId)
        }
    }

    private fun isActiveRecordingSession(state: RecordingState, taskName: String?): Boolean {
        return taskName != null && state !in setOf(
            RecordingState.IDLE,
            RecordingState.ERROR,
            RecordingState.SCHEDULED,
        )
    }

    /** 将结果、失败上传合并为时间线，并按搜索词过滤（消息与成功上传不在主界面展示） */
    private fun buildTimeline(
        uploads: List<com.timedrecorder.core.model.AudioFile>,
        results: List<com.timedrecorder.core.model.ProcessResult>,
        query: String,
    ): List<TimelineItem> {
        val items = buildList {
            // 仅保留上传失败的条目，用于展示重新上传入口
            uploads.filter { it.uploadStatus == UploadStatus.FAILED }
                .forEach { add(TimelineItem.UploadEntry(it)) }
            results.forEach { result ->
                val file = uploads.firstOrNull { it.id == result.fileId }
                add(
                    TimelineItem.ResultEntry(
                        result = result,
                        audioDuration = file?.duration,
                        audioFilePath = file?.filePath,
                    ),
                )
            }
        }.sortedByDescending { it.timestamp }

        if (query.isBlank()) return items

        val q = query.lowercase()
        return items.filter { item ->
            when (item) {
                is TimelineItem.UploadEntry ->
                    item.file.fileName.lowercase().contains(q)
                is TimelineItem.ResultEntry -> {
                    val title = item.result.title?.lowercase() ?: ""
                    val summary = item.result.summary?.lowercase() ?: ""
                    val keywords = item.result.keywords.joinToString(" ").lowercase()
                    title.contains(q) || summary.contains(q) || keywords.contains(q)
                }
                is TimelineItem.MessageEntry ->
                    item.message.title.lowercase().contains(q) ||
                        item.message.content.lowercase().contains(q)
            }
        }
    }
}
