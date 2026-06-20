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
