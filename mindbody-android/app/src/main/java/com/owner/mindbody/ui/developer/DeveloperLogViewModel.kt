package com.owner.mindbody.ui.developer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.util.AppLogBuffer
import com.owner.mindbody.util.AppLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class DeveloperLogViewModel(application: Application) : AndroidViewModel(application) {

    val entries: StateFlow<List<AppLogEntry>> = AppLogBuffer.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _autoScrollToBottom = MutableStateFlow(true)
    val autoScrollToBottom: StateFlow<Boolean> = _autoScrollToBottom.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun setAutoScrollToBottom(enabled: Boolean) {
        _autoScrollToBottom.value = enabled
    }

    fun clearLogs() {
        AppLogBuffer.clear()
        _snackbarMessage.value = "日志已清空"
    }

    fun copyAll(context: Context) {
        val text = AppLogBuffer.asPlainText()
        if (text.isBlank()) {
            _snackbarMessage.value = "暂无日志可复制"
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MindBody Logs", text))
        val lineCount = AppLogBuffer.size()
        _snackbarMessage.value = "已复制 $lineCount 行"
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }
}
