package com.timedrecorder.core.model

/**
 * 本地录音文件记录，对应 PRD §13.2 audio_file。
 */
data class AudioFile(
    val id: Long = 0,
    val taskId: Long?,
    val fileName: String,
    val filePath: String,
    val format: AudioFormat = AudioFormat.M4A,
    val startAt: Long,
    val endAt: Long?,
    val duration: Long,
    val fileSize: Long,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val uploadRetryCount: Int = 0,
    val serverFileId: String? = null,
    val processStatus: ProcessStatus = ProcessStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
)
