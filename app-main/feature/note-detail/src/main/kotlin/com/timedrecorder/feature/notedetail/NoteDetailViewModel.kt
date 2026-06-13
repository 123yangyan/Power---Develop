package com.timedrecorder.feature.notedetail

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.ProcessResult
import com.timedrecorder.feature.files.AudioPlayerController
import com.timedrecorder.feature.files.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 详情页 Tab：录音原文 / 录音总结 */
enum class NoteDetailTab {
    TRANSCRIPT,
    SUMMARY,
}

sealed interface NoteDetailUiState {
    data object Loading : NoteDetailUiState
    data class Error(val message: String) : NoteDetailUiState
    data class Success(
        val audioFile: AudioFile,
        val result: ProcessResult?,
        val fileExists: Boolean,
        val selectedTab: NoteDetailTab,
        val playerState: PlayerState,
        val progressMs: Long,
        val durationMs: Long,
        val playbackSpeed: Float,
    ) : NoteDetailUiState
}

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val audioFileRepository: AudioFileRepository,
    private val resultRepository: ResultRepository,
    val audioPlayerController: AudioPlayerController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: 0L

    private val audioFile = MutableStateFlow<AudioFile?>(null)
    private val processResult = MutableStateFlow<ProcessResult?>(null)
    private val loadError = MutableStateFlow<String?>(null)
    private val selectedTab = MutableStateFlow(NoteDetailTab.SUMMARY)

    val uiState: StateFlow<NoteDetailUiState> = combine(
        combine(
            audioFile,
            processResult,
            loadError,
            selectedTab,
        ) { file, result, error, tab ->
            DataInfo(file, result, error, tab)
        },
        combine(
            audioPlayerController.state,
            audioPlayerController.progressMs,
            audioPlayerController.durationMs,
            audioPlayerController.playbackSpeed,
        ) { playerState, progressMs, durationMs, playbackSpeed ->
            PlayerInfo(playerState, progressMs, durationMs, playbackSpeed)
        },
    ) { dataInfo, playerInfo ->
        val file = dataInfo.file
        when {
            dataInfo.error != null -> NoteDetailUiState.Error(dataInfo.error)
            file == null -> NoteDetailUiState.Loading
            else -> NoteDetailUiState.Success(
                audioFile = file,
                result = dataInfo.result,
                fileExists = File(file.filePath).exists(),
                selectedTab = dataInfo.tab,
                playerState = playerInfo.state,
                progressMs = playerInfo.progressMs,
                durationMs = playerInfo.durationMs.coerceAtLeast(file.duration),
                playbackSpeed = playerInfo.speed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteDetailUiState.Loading)

    init {
        loadData()
    }

    private fun loadData() {
        if (fileId <= 0L) {
            loadError.value = "无效的录音 ID"
            return
        }
        viewModelScope.launch {
            val file = audioFileRepository.getFileById(fileId)
            if (file == null) {
                loadError.value = "找不到该录音记录"
                return@launch
            }
            audioFile.value = file
        }
        viewModelScope.launch {
            resultRepository.observeResultByFileId(fileId).collect { result ->
                processResult.value = result
            }
        }
    }

    fun selectTab(tab: NoteDetailTab) {
        selectedTab.value = tab
    }

    fun togglePlayback() {
        val file = audioFile.value ?: return
        if (!File(file.filePath).exists()) return
        audioPlayerController.play(file.filePath, file.id)
    }

    fun seekTo(positionMs: Long) {
        audioPlayerController.seekTo(positionMs)
    }

    fun skipForward() {
        audioPlayerController.skipForward()
    }

    fun skipBackward() {
        audioPlayerController.skipBackward()
    }

    fun togglePlaybackSpeed() {
        audioPlayerController.togglePlaybackSpeed()
    }

    fun buildShareIntent(): Intent? {
        val filePath = audioFile.value?.filePath ?: return null
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "audio/*"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerController.release()
    }

    private data class DataInfo(
        val file: AudioFile?,
        val result: ProcessResult?,
        val error: String?,
        val tab: NoteDetailTab,
    )

    private data class PlayerInfo(
        val state: PlayerState,
        val progressMs: Long,
        val durationMs: Long,
        val speed: Float,
    )
}
