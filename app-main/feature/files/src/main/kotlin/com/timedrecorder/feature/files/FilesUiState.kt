package com.timedrecorder.feature.files

import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.UploadStatus

sealed interface FilesUiState {
    data object Loading : FilesUiState
    data class Success(
        val files: List<AudioFile>,
        val filter: UploadStatus?,
        /** T1：失败/待上传的文件数量，用于决定是否显示批量重试 Banner */
        val failedCount: Int = 0,
        /** T3：当前播放器状态 */
        val playerState: PlayerState = PlayerState.Idle,
        /** T3：当前正在播放的文件 ID */
        val playingFileId: Long? = null,
        /** T3：当前展开播放条的文件 ID */
        val expandedFileId: Long? = null,
        /** 正在手动拉取云端结果的文件 ID 集合 */
        val refreshingFileIds: Set<Long> = emptySet(),
    ) : FilesUiState
    data class Error(val message: String) : FilesUiState
}
