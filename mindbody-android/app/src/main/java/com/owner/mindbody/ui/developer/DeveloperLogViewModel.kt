package com.owner.mindbody.ui.developer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.util.AppLogBuffer
import com.owner.mindbody.util.AppLogEntry
import com.owner.mindbody.util.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class DeveloperLogViewModel(application: Application) : AndroidViewModel(application) {

    val totalEntries: StateFlow<List<AppLogEntry>> = AppLogBuffer.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedLevels = MutableStateFlow(LogLevel.entries.toSet())
    val selectedLevels: StateFlow<Set<LogLevel>> = _selectedLevels.asStateFlow()

    val filteredEntries: StateFlow<List<AppLogEntry>> =
        combine(AppLogBuffer.entries, _selectedLevels) { all, levels ->
            if (levels.size == LogLevel.entries.size) all
            else all.filter { it.level in levels }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _autoScrollToBottom = MutableStateFlow(true)
    val autoScrollToBottom: StateFlow<Boolean> = _autoScrollToBottom.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun toggleLevel(level: LogLevel) {
        _selectedLevels.update { current ->
            if (level in current) (current - level).ifEmpty { current }
            else current + level
        }
    }

    fun setAutoScrollToBottom(enabled: Boolean) {
        _autoScrollToBottom.value = enabled
    }

    fun clearLogs() {
        AppLogBuffer.clear()
        _snackbarMessage.value = "日志已清空"
    }

    fun copyAll(context: Context) {
        val visible = filteredEntries.value
        if (visible.isEmpty()) {
            _snackbarMessage.value = "暂无日志可复制"
            return
        }
        val text = visible.joinToString(separator = "\n") { it.formatLine() }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MindBody Logs", text))
        _snackbarMessage.value = "已复制 ${visible.size} 行"
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }
}
