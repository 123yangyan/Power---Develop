package com.timedrecorder.core.model

/**
 * 首页统一时间线条目：将结果、上传、消息按时间倒序合并展示。
 */
sealed interface TimelineItem {
    val timestamp: Long
    val id: String

    data class ResultEntry(
        val result: ProcessResult,
        /** 关联录音时长（毫秒），用于卡片展示 */
        val audioDuration: Long? = null,
        /** 关联录音本地路径，用于卡片内直接播放 */
        val audioFilePath: String? = null,
    ) : TimelineItem {
        override val timestamp: Long = result.processedAt ?: result.createdAt
        override val id: String = "result_${result.id}"
    }

    data class UploadEntry(
        val file: AudioFile,
    ) : TimelineItem {
        override val timestamp: Long = file.createdAt
        override val id: String = "upload_${file.id}"
    }

    data class MessageEntry(
        val message: MessageItem,
    ) : TimelineItem {
        override val timestamp: Long = message.createdAt
        override val id: String = "message_${message.id}"
    }
}
