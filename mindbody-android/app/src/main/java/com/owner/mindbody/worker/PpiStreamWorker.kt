package com.owner.mindbody.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.HrvOnDevice
import com.owner.mindbody.data.stream.AttemptResult
import com.owner.mindbody.data.stream.PpiLiveBuffer
import com.owner.mindbody.data.stream.PpiUploadLogBuffer
import com.owner.mindbody.data.stream.PpiWindowAttempt
import com.owner.mindbody.data.stream.SkipReason
import com.owner.mindbody.data.sync.SyncApiClient
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * PPI 实时推流 — 主路径由 [com.owner.mindbody.polar.HrStreamService] 每 90 秒调用
 * [tryStreamOnce]；本 Worker 保留为 WorkManager 15 分钟兜底（Service 被杀时）。
 *
 * 约束：联网 + BLE 已连接 + 同步开关已启用。
 */
class PpiStreamWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PpiStreamWorker"
        private const val UNIQUE_WORK_NAME = "mindbody_ppi_stream"
        /** WorkManager 最小周期 15 分钟（兜底路径）。 */
        private const val INTERVAL_MINUTES = 15L
        /** 前台服务主路径：推流间隔 90 秒。 */
        const val STREAM_INTERVAL_MS = 90_000L
        /** WorkManager 兜底：窗口回溯 120 秒。 */
        private const val WORKER_LOOKBACK_MS = 120_000L
        /** 最小有效样本数门控（与服务端 stream_routes 对齐）。 */
        private const val MIN_CLEAN_SAMPLES = 25
        /**
         * 清洗覆盖率预检：Android 端 45%，服务端 HeartPy 二次门控 60%。
         * 缓冲 15% 用于 HeartPy quotient-filter 进一步清洗；
         * 从 50% 降至 45% 可避免因边缘 coverage（如 49.6%）无谓丢包。
         */
        private const val MIN_COVERAGE_RATIO = 0.45f

        enum class StreamAttemptResult {
            /** 同步关闭 / BLE 未连 / 预热 / URL 缺失 — 未 drain，游标不推进。 */
            SKIPPED_EARLY_GATE,
            /** 已 drain 但样本不足或覆盖率低 — 游标可推进。 */
            SKIPPED_DATA,
            ACCEPTED,
            FAILED,
        }

        fun scheduleRepeating(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PpiStreamWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        /** BLE 连接建立后的信号稳定等待时间（ms）。连接初期 errorEstimate 偏高，信号不可靠。 */
        const val BLE_WARMUP_MS = 120_000L

        /**
         * 尝试上传 [sinceMs] 起的 PPI 窗口（游标由 [HrStreamService] 维护，避免固定 lookback 造成空洞）。
         *
         * @param sinceMs 窗口起始 Unix ms（含）；主路径由服务层 `lastWindowEndMs` 传入
         * @param bleConnectedAtMs BLE 连接建立时刻（Unix ms）。若传入，则连接后 [BLE_WARMUP_MS]
         *   内静默跳过推流，避免刚连上时 n_clean=0 的无效窗口污染分析链路。
         */
        suspend fun tryStreamOnce(
            app: MindBodyApplication,
            sinceMs: Long,
            bleConnectedAtMs: Long = 0L,
        ): StreamAttemptResult {
            val attemptAtMs = System.currentTimeMillis()
            val logBuffer = app.ppiUploadLogBuffer
            val prefs = app.storage.syncPreferences
            val liveBuffer = app.polarBleManager.ppiLiveBuffer

            val enabled = prefs.syncEnabled.first()
            if (!enabled) {
                AppLogger.d(TAG, "Sync disabled, skip stream")
                logBuffer.recordEarlySkip(
                    liveBuffer = liveBuffer,
                    sinceMs = sinceMs,
                    attemptAtMs = attemptAtMs,
                    skipReason = SkipReason.SYNC_DISABLED,
                )
                return StreamAttemptResult.SKIPPED_EARLY_GATE
            }
            if (app.polarBleManager.connectionState.value != ConnectionState.CONNECTED) {
                AppLogger.d(TAG, "BLE not connected, skip stream")
                logBuffer.recordEarlySkip(
                    liveBuffer = liveBuffer,
                    sinceMs = sinceMs,
                    attemptAtMs = attemptAtMs,
                    skipReason = SkipReason.BLE_DISCONNECTED,
                )
                return StreamAttemptResult.SKIPPED_EARLY_GATE
            }
            if (bleConnectedAtMs > 0L) {
                val warmupRemaining = bleConnectedAtMs + BLE_WARMUP_MS - System.currentTimeMillis()
                if (warmupRemaining > 0) {
                    AppLogger.d(TAG, "BLE warmup: ${warmupRemaining / 1000}s remaining, skip stream")
                    logBuffer.recordEarlySkip(
                        liveBuffer = liveBuffer,
                        sinceMs = sinceMs,
                        attemptAtMs = attemptAtMs,
                        skipReason = SkipReason.BLE_WARMUP,
                        errorMessage = "剩余 ${warmupRemaining / 1000}s",
                    )
                    return StreamAttemptResult.SKIPPED_EARLY_GATE
                }
            }

            val baseUrl = prefs.baseUrl.first()
            if (baseUrl.isBlank()) {
                AppLogger.d(TAG, "Sync URL missing, skip stream")
                logBuffer.recordEarlySkip(
                    liveBuffer = liveBuffer,
                    sinceMs = sinceMs,
                    attemptAtMs = attemptAtMs,
                    skipReason = SkipReason.NO_BASE_URL,
                )
                return StreamAttemptResult.SKIPPED_EARLY_GATE
            }
            val apiKey = prefs.apiKey.first()
            val deviceId = prefs.getOrCreateDeviceId()

            val apiClient = SyncApiClient(baseUrl, apiKey)

            val drained = liveBuffer.drainWindowAtomic(sinceMs)
            val allSamples = drained.allSamples
            val rrList = drained.cleanRrMs

            val nRaw = allSamples.size
            val nClean = rrList.size
            val coverageRatio = if (nRaw > 0) nClean.toFloat() / nRaw else 0f
            val windowStartTs = allSamples.firstOrNull()?.timestampMs ?: sinceMs
            val windowEndTs = allSamples.lastOrNull()?.timestampMs ?: attemptAtMs

            val coveragePct = coverageRatio * 100f
            AppLogger.d(TAG, "Gate check: sinceMs=$sinceMs nRaw=$nRaw nClean=$nClean coverage=${"%.1f".format(coveragePct)}%")

            if (nClean < MIN_CLEAN_SAMPLES) {
                AppLogger.d(TAG, "Insufficient clean samples: $nClean < $MIN_CLEAN_SAMPLES, skip")
                logBuffer.recordSkip(
                    attemptAtMs = attemptAtMs,
                    skipReason = SkipReason.INSUFFICIENT_CLEAN,
                    windowStartMs = windowStartTs,
                    windowEndMs = windowEndTs,
                    nRaw = nRaw,
                    nClean = nClean,
                    coverageRatio = coverageRatio,
                )
                return StreamAttemptResult.SKIPPED_DATA
            }
            if (nRaw > 0 && coveragePct < MIN_COVERAGE_RATIO * 100f) {
                AppLogger.d(
                    TAG,
                    "Insufficient coverage: ${"%.1f".format(coveragePct)}% < ${MIN_COVERAGE_RATIO * 100}%, skip"
                )
                logBuffer.recordSkip(
                    attemptAtMs = attemptAtMs,
                    skipReason = SkipReason.LOW_COVERAGE,
                    windowStartMs = windowStartTs,
                    windowEndMs = windowEndTs,
                    nRaw = nRaw,
                    nClean = nClean,
                    coverageRatio = coverageRatio,
                )
                return StreamAttemptResult.SKIPPED_DATA
            }

            val rmssd = HrvOnDevice.rmssd(rrList)
            val sdnn = HrvOnDevice.sdnn(rrList)

            val accMagnitudes = allSamples
                .mapNotNull { it.accMagnitudeMg }
                .filter { it > 0 }
            val accMean = if (accMagnitudes.isNotEmpty()) accMagnitudes.average() else null

            val payload = SyncApiClient.PpiWindowPayload(
                deviceId = deviceId,
                windowStartTs = windowStartTs,
                windowEndTs = windowEndTs,
                rrListMs = rrList,
                nRaw = nRaw,
                nClean = nClean,
                onDeviceRmssd = rmssd,
                onDeviceSdnn = sdnn,
                accMagnitudeMean = accMean
            )

            AppLogger.d(TAG, "Streaming window: n=$nClean rmssd=$rmssd sdnn=$sdnn sinceMs=$sinceMs")

            val result = apiClient.postPpiWindow(payload)
            return if (result.accepted) {
                AppLogger.d(TAG, "Stream OK: windowId=${result.windowId}")
                logBuffer.add(
                    PpiWindowAttempt(
                        attemptAtMs = attemptAtMs,
                        windowStartMs = windowStartTs,
                        windowEndMs = windowEndTs,
                        nRaw = nRaw,
                        nClean = nClean,
                        coverageRatio = coverageRatio,
                        result = AttemptResult.ACCEPTED,
                        serverAccepted = true,
                        serverWindowId = result.windowId,
                    )
                )
                StreamAttemptResult.ACCEPTED
            } else {
                AppLogger.w(TAG, "Stream failed: ${result.error}")
                logBuffer.add(
                    PpiWindowAttempt(
                        attemptAtMs = attemptAtMs,
                        windowStartMs = windowStartTs,
                        windowEndMs = windowEndTs,
                        nRaw = nRaw,
                        nClean = nClean,
                        coverageRatio = coverageRatio,
                        result = AttemptResult.FAILED,
                        serverAccepted = false,
                        errorMessage = result.error ?: "服务端拒绝",
                    )
                )
                StreamAttemptResult.FAILED
            }
        }

        private fun PpiUploadLogBuffer.recordEarlySkip(
            liveBuffer: PpiLiveBuffer,
            sinceMs: Long,
            attemptAtMs: Long,
            skipReason: SkipReason,
            errorMessage: String? = null,
        ) {
            val peek = liveBuffer.drainWindowAtomic(sinceMs)
            val nRaw = peek.allSamples.size
            val nClean = peek.cleanRrMs.size
            val coverageRatio = if (nRaw > 0) nClean.toFloat() / nRaw else 0f
            recordSkip(
                attemptAtMs = attemptAtMs,
                skipReason = skipReason,
                windowStartMs = peek.allSamples.firstOrNull()?.timestampMs ?: sinceMs,
                windowEndMs = peek.allSamples.lastOrNull()?.timestampMs ?: attemptAtMs,
                nRaw = nRaw,
                nClean = nClean,
                coverageRatio = coverageRatio,
                errorMessage = errorMessage,
            )
        }

        private fun PpiUploadLogBuffer.recordSkip(
            attemptAtMs: Long,
            skipReason: SkipReason,
            windowStartMs: Long = 0L,
            windowEndMs: Long = 0L,
            nRaw: Int = 0,
            nClean: Int = 0,
            coverageRatio: Float = 0f,
            errorMessage: String? = null,
        ) {
            add(
                PpiWindowAttempt(
                    attemptAtMs = attemptAtMs,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs,
                    nRaw = nRaw,
                    nClean = nClean,
                    coverageRatio = coverageRatio,
                    result = AttemptResult.SKIPPED,
                    skipReason = skipReason,
                    errorMessage = errorMessage,
                )
            )
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? MindBodyApplication ?: return Result.failure()
        val sinceMs = System.currentTimeMillis() - WORKER_LOOKBACK_MS
        return when (tryStreamOnce(app, sinceMs = sinceMs)) {
            StreamAttemptResult.ACCEPTED,
            StreamAttemptResult.SKIPPED_EARLY_GATE,
            StreamAttemptResult.SKIPPED_DATA,
            -> Result.success()
            StreamAttemptResult.FAILED -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
