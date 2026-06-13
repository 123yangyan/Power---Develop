package com.timedrecorder.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.MessageRepository
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
class MessagesViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val alertsOnly = MutableStateFlow(false)

    val uiState: StateFlow<MessagesUiState> = combine(
        messageRepository.observeAllMessages(),
        messageRepository.observeUnreadCount(),
        alertsOnly,
    ) { messages, unread, alerts ->
        val filtered = if (alerts) messages.filter { it.type.name == "ALERT" } else messages
        MessagesUiState.Success(filtered, unread, alerts) as MessagesUiState
    }.catch { emit(MessagesUiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState.Loading)

    fun toggleAlertsOnly() {
        alertsOnly.value = !alertsOnly.value
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { messageRepository.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { messageRepository.markAllAsRead() }
    }
}
