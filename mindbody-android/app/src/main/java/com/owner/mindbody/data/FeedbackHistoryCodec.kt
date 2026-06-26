package com.owner.mindbody.data

import org.json.JSONArray
import org.json.JSONObject

/** LLM 反馈历史的 JSON 序列化/反序列化（DataStore 持久化 + API 响应解析）。 */
object FeedbackHistoryCodec {

    fun fromApiResponse(json: String): List<LlmFeedbackEntry> = runCatching {
        val arr = JSONObject(json).optJSONArray("feedback_history") ?: return emptyList()
        decodeArray(arr)
    }.getOrElse { emptyList() }

    fun fromStoredJson(json: String): List<LlmFeedbackEntry> = runCatching {
        if (json.isBlank()) return emptyList()
        decodeArray(JSONArray(json))
    }.getOrElse { emptyList() }

    fun toStoredJson(entries: List<LlmFeedbackEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry -> arr.put(encodeEntry(entry)) }
        return arr.toString()
    }

    private fun decodeArray(arr: JSONArray): List<LlmFeedbackEntry> {
        return (0 until arr.length()).map { i ->
            decodeEntry(arr.getJSONObject(i))
        }
    }

    private fun decodeEntry(item: JSONObject): LlmFeedbackEntry {
        return LlmFeedbackEntry(
            id = item.optLong("id"),
            timestampMs = item.optLong("created_at_ms", item.optLong("timestamp_ms", System.currentTimeMillis())),
            stateLabel = item.optString("state_label", "normal"),
            anxietyScore = item.optDouble("anxiety_score", 0.0).toFloat(),
            message = item.optString("message", ""),
            tone = item.optString("tone", ""),
            userResponse = item.optString("user_response").takeIf { it.isNotEmpty() }
        )
    }

    private fun encodeEntry(entry: LlmFeedbackEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("created_at_ms", entry.timestampMs)
            put("state_label", entry.stateLabel)
            put("anxiety_score", entry.anxietyScore.toDouble())
            put("message", entry.message)
            put("tone", entry.tone)
            entry.userResponse?.let { put("user_response", it) }
        }
    }
}
