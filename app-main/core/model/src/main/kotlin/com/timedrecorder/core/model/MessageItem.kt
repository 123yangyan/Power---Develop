package com.timedrecorder.core.model

/**
 * 消息中心条目，对应 PRD §13.4 message_center。
 */
data class MessageItem(
    val id: Long = 0,
    val title: String,
    val content: String,
    val type: MessageType = MessageType.ALERT,
    val fileId: Long?,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
