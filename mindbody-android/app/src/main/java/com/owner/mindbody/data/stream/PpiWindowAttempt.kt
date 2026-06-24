package com.owner.mindbody.data.stream

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** PPI 窗口上传尝试结果。 */
enum class AttemptResult {
    ACCEPTED,
    SKIPPED,
    FAILED;

    fun label(): String = when (this) {
        ACCEPTED -> "已上传"
        SKIPPED -> "未上传"
        FAILED -> "失败"
    }
}

/** 未上传时的跳过原因（与服务端门控对齐）。 */
enum class SkipReason {
    SYNC_DISABLED,
    BLE_DISCONNECTED,
    BLE_WARMUP,
    INSUFFICIENT_CLEAN,
    LOW_COVERAGE,
    NO_BASE_URL;

    fun label(): String = when (this) {
        SYNC_DISABLED -> "同步已关闭"
        BLE_DISCONNECTED -> "BLE 未连接"
        BLE_WARMUP -> "Polar 信号稳定期"
        INSUFFICIENT_CLEAN -> "有效样本不足 (<25)"
        LOW_COVERAGE -> "覆盖率不足 (<45%)"
        NO_BASE_URL -> "未配置 Server URL"
    }
}

/**
 * 单次 PPI 窗口上传尝试记录，供开发者诊断页展示。
 * [id] 由 [PpiUploadLogBuffer] 在写入时分配。
 */
data class PpiWindowAttempt(
    val id: Long = 0L,
    val attemptAtMs: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val nRaw: Int,
    val nClean: Int,
    val coverageRatio: Float,
    val result: AttemptResult,
    val skipReason: SkipReason? = null,
    val serverAccepted: Boolean? = null,
    val serverWindowId: Long? = null,
    val errorMessage: String? = null,
) {
    fun formatLine(): String {
        val time = TIME_FORMAT.format(Date(attemptAtMs))
        val coveragePct = "%.1f".format(coverageRatio * 100f)
        val sampleInfo = if (nRaw > 0 || nClean > 0) {
            "nRaw=$nRaw nClean=$nClean cov=$coveragePct%"
        } else {
            "无样本"
        }
        val detail = when (result) {
            AttemptResult.ACCEPTED -> {
                val windowId = serverWindowId?.let { " windowId=$it" }.orEmpty()
                "已上传$windowId"
            }
            AttemptResult.SKIPPED -> {
                val base = skipReason?.label() ?: "已跳过"
                if (errorMessage != null) "$base · $errorMessage" else base
            }
            AttemptResult.FAILED -> errorMessage ?: "上传失败"
        }
        return "$time [${result.label()}] $sampleInfo · $detail"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("attempt_at_ms", attemptAtMs)
        put("window_start_ms", windowStartMs)
        put("window_end_ms", windowEndMs)
        put("n_raw", nRaw)
        put("n_clean", nClean)
        put("coverage_ratio", coverageRatio.toDouble())
        put("result", result.name)
        put("skip_reason", skipReason?.name)
        put("server_accepted", serverAccepted)
        put("server_window_id", serverWindowId)
        put("error_message", errorMessage)
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
