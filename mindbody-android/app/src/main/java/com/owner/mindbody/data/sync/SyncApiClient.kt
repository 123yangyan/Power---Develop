package com.owner.mindbody.data.sync

import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 身心数据云端同步 HTTP 客户端。
 * 使用 OkHttp + org.json（Android 内置），无需额外序列化依赖。
 */
class SyncApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "SyncApiClient"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class BatchResult(val inserted: Int, val skipped: Int, val error: String? = null)

    // ---------------------------------------------------------------
    // PPI 实时推流
    // ---------------------------------------------------------------

    data class PpiWindowPayload(
        val deviceId: String,
        val windowStartTs: Long,
        val windowEndTs: Long,
        val rrListMs: List<Int>,
        val nRaw: Int,
        val nClean: Int,
        val onDeviceRmssd: Double,
        val onDeviceSdnn: Double,
        val accMagnitudeMean: Double?
    )

    data class PpiWindowResult(val windowId: Long?, val accepted: Boolean, val error: String? = null)

    suspend fun postPpiWindow(payload: PpiWindowPayload): PpiWindowResult = withContext(Dispatchers.IO) {
        try {
            val rrArray = JSONArray()
            payload.rrListMs.forEach { rrArray.put(it) }

            val body = JSONObject().apply {
                put("device_id", payload.deviceId)
                put("window_start_ts", payload.windowStartTs)
                put("window_end_ts", payload.windowEndTs)
                put("rr_list_ms", rrArray)
                put("n_raw", payload.nRaw)
                put("n_clean", payload.nClean)
                put("on_device_rmssd", payload.onDeviceRmssd)
                put("on_device_sdnn", payload.onDeviceSdnn)
                if (payload.accMagnitudeMean != null) {
                    put("acc_magnitude_mean", payload.accMagnitudeMean)
                }
            }

            val url = "${baseUrl.trimEnd('/')}/api/vitals/stream/ppi-window"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val windowId = json.optLong("window_id", -1L).takeIf { it >= 0 }
                val accepted = json.optBoolean("accepted", false)
                PpiWindowResult(windowId, accepted = accepted)
            } else {
                AppLogger.w(TAG, "postPpiWindow failed: HTTP ${response.code} $responseBody")
                PpiWindowResult(null, false, "HTTP ${response.code}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "postPpiWindow error", e)
            PpiWindowResult(null, false, e.message)
        }
    }

    // ---------------------------------------------------------------
    // FCM token 注册 & 通知响应回报
    // ---------------------------------------------------------------

    /**
     * 向服务器注册/更新 FCM 推送 token。
     * 每次 FirebaseMessagingService.onNewToken 回调时调用。
     */
    suspend fun registerFcmToken(deviceId: String, fcmToken: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("device_id", deviceId)
                    put("fcm_token", fcmToken)
                }
                val url = "${baseUrl.trimEnd('/')}/api/vitals/stream/register-token"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
                val response = client.newCall(request).execute()
                val ok = response.isSuccessful
                response.close()
                if (!ok) AppLogger.w(TAG, "registerFcmToken failed: HTTP ${response.code}")
                ok
            } catch (e: Exception) {
                AppLogger.e(TAG, "registerFcmToken error", e)
                false
            }
        }

    /**
     * 回报用户对推送通知的操作。
     * @param response "logged" | "snoozed" | "dismissed"
     */
    suspend fun reportNotificationResponse(
        deviceId: String,
        notificationId: Int?,
        response: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                if (notificationId != null) put("notification_id", notificationId)
                put("response", response)
            }
            val url = "${baseUrl.trimEnd('/')}/api/vitals/stream/notification-response"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            val response2 = client.newCall(request).execute()
            val ok = response2.isSuccessful
            response2.close()
            ok
        } catch (e: Exception) {
            AppLogger.e(TAG, "reportNotificationResponse error", e)
            false
        }
    }

    /**
     * 批量上报一张表的数据。
     *
     * @param deviceId  设备标识
     * @param source    "LIVE_STREAM" | "OFFLINE_SYNC" | "USER_INPUT"
     * @param table     目标表名（如 "hr_samples"）
     * @param columns   该表的指标列名列表
     * @param rows      数据行：每行是一个 Map（key 为列名，value 为 Any?）
     */
    suspend fun uploadBatch(
        deviceId: String,
        source: String,
        table: String,
        columns: List<String>,
        rows: List<Map<String, Any?>>
    ): BatchResult = withContext(Dispatchers.IO) {
        try {
            val rowsArray = JSONArray()
            for (row in rows) {
                val obj = JSONObject()
                obj.put("clientRowId", row["clientRowId"]?.toString() ?: "")
                obj.put("ts", row["ts"] ?: 0L)
                for (col in columns) {
                    val v = row[col]
                    when (v) {
                        null -> obj.put(col, JSONObject.NULL)
                        is Boolean -> obj.put(col, v)
                        is Number -> obj.put(col, v)
                        else -> obj.put(col, v.toString())
                    }
                }
                rowsArray.put(obj)
            }

            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("source", source)
                put("table", table)
                put("rows", rowsArray)
            }

            val url = "${baseUrl.trimEnd('/')}/api/vitals/batch"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val data = json.optJSONObject("data")
                val inserted = data?.optInt("inserted", 0) ?: 0
                val skipped = data?.optInt("skipped", 0) ?: 0
                AppLogger.d(TAG, "Upload $table: inserted=$inserted skipped=$skipped")
                BatchResult(inserted, skipped)
            } else {
                AppLogger.w(TAG, "Upload $table failed: HTTP ${response.code} $responseBody")
                BatchResult(0, 0, "HTTP ${response.code}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Upload $table error", e)
            BatchResult(0, 0, e.message)
        }
    }
}
