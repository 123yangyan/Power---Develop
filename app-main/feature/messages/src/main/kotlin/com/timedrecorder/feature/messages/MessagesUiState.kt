package com.timedrecorder.feature.messages

import com.timedrecorder.core.model.MessageItem

sealed interface MessagesUiState {
    data object Loading : MessagesUiState
    data class Success(
        val messages: List<MessageItem>,
        val unreadCount: Int,
        val showAlertsOnly: Boolean,
    ) : MessagesUiState
    data class Error(val message: String) : MessagesUiState
}
