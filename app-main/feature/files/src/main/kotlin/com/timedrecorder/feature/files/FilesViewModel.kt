package com.timedrecorder.feature.files

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.data.repository.UploadRepository
import com.timedrecorder.core.data.scheduler.UploadScheduler
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val audioFileRepository: AudioFileRepository,
    private val uploadRepository: UploadRepository,
    private val resultRepository: ResultRepository,
    private val uploadScheduler: UploadScheduler,
    private val logRepository: LogRepository,
    val audioPlayerController: AudioPlayerController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** 正在手动拉取结果的文件 ID */
    private val refreshingFileIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 手动/批量重传操作反馈（供 UI Toast） */
    private val _retryFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val retryFeedback: SharedFlow<String> = _retryFeedback.asSharedFlow()

    /** 删除操作反馈（供 UI Toast） */
    private val _deleteFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val deleteFeedback: SharedFlow<String> = _deleteFeedback.asSharedFlow()

    private val filter = MutableStateFlow<UploadStatus?>(null)

    /** T1：防重复点击状态位 */
    private val bulkRetryTriggered = MutableStateFlow(false)

    /** T3：当前展开播放条的文件 ID（null 表示全部折叠） */
    private val expandedFileId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<FilesUiState> = combine(
        combine(
            audioFileRepository.observeAllFiles(),
            filter,
            refreshingFileIds,
        ) { files, currentFilter, refreshingIds -> Triple(files, currentFilter, refreshingIds) },
        combine(
            audioPlayerController.state,
            audioPlayerController.playingFileId,
            expandedFileId,
        ) { playerState, playingFileId, expandedId -> Triple(playerState, playingFileId, expandedId) },
    ) { fileInfo, playerInfo ->
        val files = fileInfo.first
        val currentFilter = fileInfo.second
        val refreshingIds = fileInfo.third
        val playerState = playerInfo.first
        val playingFileId = playerInfo.second
        val expandedId = playerInfo.third
        val filtered = currentFilter?.let { f -> files.filter { it.uploadStatus == f } } ?: files
        val failedCount = files.count { it.uploadStatus.canManualRetry() }
        FilesUiState.Success(
            files = filtered,
            filter = currentFilter,
            failedCount = failedCount,
            playerState = playerState,
            playingFileId = playingFileId,
            expandedFileId = expandedId,
            refreshingFileIds = refreshingIds,
        ) as FilesUiState
    }.catch { emit(FilesUiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState.Loading)

    /** T3：播放进度（毫秒），直接暴露以避免频繁触发完整 uiState 重组 */
    val progressMs: StateFlow<Long> = audioPlayerController.progressMs

    /** T3：文件总时长（毫秒） */
    val durationMs: StateFlow<Long> = audioPlayerController.durationMs

    fun setFilter(status: UploadStatus?) {
        filter.value = status
    }

    /** T1：单文件手动重试（前台直接上传，成功后自动轮询结果） */
    fun retryUpload(fileId: Long) {
        viewModelScope.launch {
            val file = audioFileRepository.getFileById(fileId)
            if (file == null) {
                _retryFeedback.emit("文件不存在")
                return@launch
            }
            if (file.uploadStatus == UploadStatus.SUCCESS) {
                _retryFeedback.emit("该文件已上传成功")
                return@launch
            }
            audioFileRepository.resetUploadForRetry(fileId)
            logRepository.log(LogType.UPLOAD, LogLevel.INFO, "手动上传开始: ${file.fileName}")
            _retryFeedback.emit("正在上传…")
            uploadRepository.uploadFile(fileId)
                .onSuccess { serverFileId ->
                    uploadScheduler.enqueuePoll(serverFileId, fileId)
                    logRepository.log(
                        LogType.UPLOAD,
                        LogLevel.INFO,
                        "手动上传成功: ${file.fileName} -> $serverFileId",
                    )
                    _retryFeedback.emit("上传成功")
                }
                .onFailure { error ->
                    logRepository.log(
                        LogType.UPLOAD,
                        LogLevel.ERROR,
                        "手动上传失败: ${file.fileName} - ${error.message}",
                    )
                    _retryFeedback.emit("上传失败：${error.message ?: "未知错误"}")
                }
        }
    }

    /** 手动拉取云端处理结果（已上传但本地无摘要时使用） */
    fun refreshResult(fileId: Long) {
        if (refreshingFileIds.value.contains(fileId)) return
        viewModelScope.launch {
            val file = audioFileRepository.getFileById(fileId) ?: return@launch
            val serverFileId = file.serverFileId
            if (file.uploadStatus != UploadStatus.SUCCESS || serverFileId.isNullOrBlank()) return@launch
            if (file.processStatus == ProcessStatus.COMPLETED) return@launch

            refreshingFileIds.value += fileId
            try {
                resultRepository.pollResult(serverFileId, fileId)
            } finally {
                refreshingFileIds.value -= fileId
            }
        }
    }

    /**
     * T1：批量重试所有失败/待上传文件。
     * 内置 1 秒防重复点击，防止多次触发 WorkManager 任务入队。
     */
    fun retryAllFailed() {
        if (bulkRetryTriggered.value) return
        bulkRetryTriggered.value = true
        viewModelScope.launch {
            val pendingFiles = audioFileRepository.observeAllFiles().first()
                .filter { it.uploadStatus.canManualRetry() }
            if (pendingFiles.isEmpty()) {
                _retryFeedback.emit("没有需要重传的文件")
                bulkRetryTriggered.value = false
                return@launch
            }
            audioFileRepository.resetAllPendingUploadsForRetry()
            _retryFeedback.emit("正在上传 ${pendingFiles.size} 个文件…")
            var successCount = 0
            var failCount = 0
            for (file in pendingFiles) {
                logRepository.log(LogType.UPLOAD, LogLevel.INFO, "批量手动上传开始: ${file.fileName}")
                uploadRepository.uploadFile(file.id)
                    .onSuccess { serverFileId ->
                        uploadScheduler.enqueuePoll(serverFileId, file.id)
                        successCount++
                        logRepository.log(
                            LogType.UPLOAD,
                            LogLevel.INFO,
                            "批量手动上传成功: ${file.fileName} -> $serverFileId",
                        )
                    }
                    .onFailure { error ->
                        failCount++
                        logRepository.log(
                            LogType.UPLOAD,
                            LogLevel.ERROR,
                            "批量手动上传失败: ${file.fileName} - ${error.message}",
                        )
                    }
            }
            _retryFeedback.emit("上传完成：成功 $successCount 个，失败 $failCount 个")
            kotlinx.coroutines.delay(1_000L)
            bulkRetryTriggered.value = false
        }
    }

    /** 删除本地录音及关联摘要、消息 */
    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            if (audioPlayerController.playingFileId.value == fileId) {
                audioPlayerController.release()
            }
            if (expandedFileId.value == fileId) {
                expandedFileId.value = null
            }
            uploadScheduler.cancelFileWork(fileId)
            audioFileRepository.deleteRecordingCompletely(fileId)
                .onSuccess { _deleteFeedback.emit("已删除") }
                .onFailure { _deleteFeedback.emit("删除失败：${it.message ?: "未知错误"}") }
        }
    }

    /** T3：点击文件卡片展开/折叠播放条 */
    fun toggleExpanded(fileId: Long) {
        expandedFileId.value = if (expandedFileId.value == fileId) null else fileId
    }

    /** T3：切换指定文件的播放状态 */
    fun togglePlayback(fileId: Long, filePath: String) {
        audioPlayerController.play(filePath, fileId)
    }

    /** T3：拖拽进度条 */
    fun seekTo(positionMs: Long) {
        audioPlayerController.seekTo(positionMs)
    }

    /**
     * T9：构建系统分享 Intent。
     * Android 7+ 必须使用 FileProvider 获取 content:// URI，
     * 直接使用 file:// URI 会抛出 FileUriExposedException。
     */
    fun buildShareIntent(filePath: String): Intent? {
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
        // T3：ViewModel 销毁时释放 MediaPlayer 资源
        audioPlayerController.release()
    }
}

/** 是否可手动重传（含卡死的 UPLOADING 状态） */
private fun UploadStatus.canManualRetry(): Boolean = when (this) {
    UploadStatus.PENDING,
    UploadStatus.FAILED,
    UploadStatus.RETRYING,
    UploadStatus.UPLOADING,
    -> true
    else -> false
}
