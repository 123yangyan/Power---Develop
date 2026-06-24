package com.owner.mindbody.ui.developer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.stream.AttemptResult
import com.owner.mindbody.data.stream.PpiUploadLogBuffer
import com.owner.mindbody.data.stream.PpiWindowAttempt
import com.owner.mindbody.data.stream.SkipReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SkipBreakdownItem(
    val reason: SkipReason,
    val count: Int,
    val pctOfSkipped: Float,
)

data class PpiUploadLogSummary(
    val totalCount: Int = 0,
    val acceptedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val uploadRatePct: Float = 0f,
    val avgCoveragePct: Float = 0f,
    val skipBreakdown: List<SkipBreakdownItem> = emptyList(),
    val recentTimeline: List<AttemptResult> = emptyList(),
)

class PpiUploadLogViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TIMELINE_MAX = 30
    }

    private val logBuffer: PpiUploadLogBuffer =
        (application as MindBodyApplication).ppiUploadLogBuffer

    val entries: StateFlow<List<PpiWindowAttempt>> = logBuffer.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<PpiUploadLogSummary> = logBuffer.entries
        .map { list -> computeSummary(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PpiUploadLogSummary())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearLogs() {
        logBuffer.clear()
        _snackbarMessage.value = "日志已清空"
    }

    fun copyAll(context: Context) {
        val text = logBuffer.asJsonText()
        if (text.isBlank() || text == "[]") {
            _snackbarMessage.value = "暂无日志可复制"
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MindBody PPI Upload Log", text))
        _snackbarMessage.value = "已复制 ${logBuffer.size()} 条 JSON"
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private fun computeSummary(list: List<PpiWindowAttempt>): PpiUploadLogSummary {
        if (list.isEmpty()) return PpiUploadLogSummary()

        val acceptedCount = list.count { it.result == AttemptResult.ACCEPTED }
        val skippedCount = list.count { it.result == AttemptResult.SKIPPED }
        val failedCount = list.count { it.result == AttemptResult.FAILED }
        val totalCount = list.size

        val withSamples = list.filter { it.nRaw > 0 }
        val avgCoverage = if (withSamples.isNotEmpty()) {
            withSamples.map { it.coverageRatio }.average().toFloat() * 100f
        } else {
            0f
        }

        val skipByReason = list
            .filter { it.result == AttemptResult.SKIPPED && it.skipReason != null }
            .groupingBy { it.skipReason!! }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { (reason, count) ->
                SkipBreakdownItem(
                    reason = reason,
                    count = count,
                    pctOfSkipped = if (skippedCount > 0) count.toFloat() / skippedCount * 100f else 0f,
                )
            }

        val recentTimeline = list.takeLast(TIMELINE_MAX).map { it.result }

        return PpiUploadLogSummary(
            totalCount = totalCount,
            acceptedCount = acceptedCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            uploadRatePct = acceptedCount.toFloat() / totalCount * 100f,
            avgCoveragePct = avgCoverage,
            skipBreakdown = skipByReason,
            recentTimeline = recentTimeline,
        )
    }
}
