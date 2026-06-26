package com.owner.mindbody.ui.physio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.LlmFeedbackEntry
import com.owner.mindbody.data.PhysioStateSummary
import com.owner.mindbody.notification.PhysioNotificationManager
import com.owner.mindbody.polar.AccSample
import com.polar.sdk.api.model.PolarPpiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 生理状态页 ViewModel。
 *
 * 短期策略（Phase 2/3 过渡期）：每 30s 轮询服务端 API，
 * 将结果写入 AppStorage 门面 Flow，UI 通过 Flow 收到更新。
 * 待 Phase 3 完成后可无缝改为订阅 Room Cache Flow。
 */
class PhysioStateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val storage = app.storage
    private val httpClient = OkHttpClient()

    val currentAcc: StateFlow<AccSample?> = app.polarBleManager.currentAcc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestPpi: StateFlow<PolarPpiData.PolarPpiSample?> = app.polarBleManager.latestPpi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentSkinTemp: StateFlow<Float?> = app.polarBleManager.currentSkinTemp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestPhysioState: StateFlow<PhysioStateSummary?> =
        storage.latestPhysioState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val feedbackHistory: StateFlow<List<LlmFeedbackEntry>> =
        storage.feedbackHistory.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private var pollJob: Job? = null
    private var lastNotifiedLabel: String? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetchPhysioState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun fetchPhysioState() {
        try {
            withContext(Dispatchers.IO) {
                val baseUrl = storage.syncPreferences.baseUrl.first()
                val apiKey = storage.syncPreferences.apiKey.first()
                val deviceId = storage.syncPreferences.deviceId.first()
                if (baseUrl.isBlank() || apiKey.isBlank() || deviceId.isBlank()) return@withContext

                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/vitals/stream/status?device_id=$deviceId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val body = response.body?.string() ?: return@withContext
                    val summary = parsePhysioStateSummary(body)
                    if (summary != null) {
                        storage.updatePhysioState(summary)
                        maybeNotifyAlert(summary)
                    }

                    val feedback = parseFeedbackHistory(body)
                    if (feedback.isNotEmpty()) {
                        storage.updateFeedbackHistory(feedback)
                    }
                }
            }
        } catch (_: Exception) {
            // 网络失败静默处理，保留上次状态
        }
    }

    private fun maybeNotifyAlert(summary: PhysioStateSummary) {
        val label = summary.stateLabel
        if (label !in ALERT_LABELS) {
            lastNotifiedLabel = null
            return
        }
        if (label == lastNotifiedLabel) return

        val message = summary.llmMessage?.takeIf { it.isNotBlank() } ?: defaultAlertMessage(label)
        val notificationId = summary.windowId
            ?.let { (it % Int.MAX_VALUE).toInt() }
            ?.takeIf { it != 0 }
            ?: NOTIFICATION_FALLBACK_ID

        PhysioNotificationManager.show(
            context = getApplication(),
            notificationId = notificationId,
            stateLabel = label,
            message = message
        )
        lastNotifiedLabel = label
    }

    private fun defaultAlertMessage(stateLabel: String): String = when (stateLabel) {
        "high_anxiety" -> "生理指标显示高度焦虑倾向，建议暂停活动并关注当下感受。"
        "anxious" -> "生理指标显示焦虑倾向，建议适当休息。"
        else -> "检测到轻度应激反应，建议适当休息。"
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_FALLBACK_ID = 3001
        private val ALERT_LABELS = setOf("elevated", "anxious", "high_anxiety")
    }
}

// ── 简易 JSON 解析（Phase 3 替换为 Moshi/Gson）────────────────────────────────

private fun parsePhysioStateSummary(json: String): PhysioStateSummary? = runCatching {
    val obj = JSONObject(json)
    val latest = obj.optJSONObject("latest_classification") ?: return null
    val hrv = obj.optJSONObject("latest_hrv")
    val feedback = obj.optJSONObject("latest_feedback")

    PhysioStateSummary(
        stateLabel = latest.optString("state_label", "baseline_building"),
        anxietyScore = latest.optDouble("anxiety_score", 0.0).toFloat(),
        rmssd = hrv?.optDouble("rmssd")?.toFloat(),
        sdnn = hrv?.optDouble("sdnn")?.toFloat(),
        lfHf = hrv?.optDouble("lf_hf")?.toFloat(),
        breathingRate = hrv?.optDouble("breathing_rate")?.toFloat(),
        sampEn = hrv?.optDouble("sampen")?.toFloat(),
        dfaAlpha1 = hrv?.optDouble("dfa_alpha1")?.toFloat(),
        hrSurgeFlag = hrv?.optBoolean("hr_surge_flag") ?: false,
        windowId = latest.optLong("window_id").takeIf { it != 0L },
        timestampMs = latest.optLong("created_at_ms", System.currentTimeMillis()),
        llmMessage = feedback?.optString("message"),
        baselineWindowCount = obj.optInt("window_count", 0),
        lastStreamTs = obj.optLong("last_stream_ts").takeIf { it != 0L }
    )
}.getOrNull()

private fun parseFeedbackHistory(json: String): List<LlmFeedbackEntry> = runCatching {
    val arr = JSONObject(json).optJSONArray("feedback_history") ?: return@runCatching emptyList()
    (0 until arr.length()).map { i ->
        val item = arr.getJSONObject(i)
        LlmFeedbackEntry(
            id = item.optLong("id"),
            timestampMs = item.optLong("created_at_ms", System.currentTimeMillis()),
            stateLabel = item.optString("state_label", "normal"),
            anxietyScore = item.optDouble("anxiety_score", 0.0).toFloat(),
            message = item.optString("message", ""),
            tone = item.optString("tone", ""),
            userResponse = item.optString("user_response").takeIf { it.isNotEmpty() }
        )
    }
}.getOrElse { emptyList() }
