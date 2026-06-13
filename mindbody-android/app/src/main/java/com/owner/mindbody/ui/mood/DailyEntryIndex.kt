package com.owner.mindbody.ui.mood

import com.owner.mindbody.data.local.MoodEntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 同日序号元数据（1-based） */
data class DailyEntryIndexMeta(
    val index: Int,
    val total: Int
)

private val dateKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun moodEntryDateKey(occurredAtMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    return dateKeyFormatter.format(Instant.ofEpochMilli(occurredAtMs).atZone(zoneId))
}

fun todayDateKey(zoneId: ZoneId = ZoneId.systemDefault()): String {
    return dateKeyFormatter.format(java.time.LocalDate.now(zoneId))
}

/** 某日全部记录（按 occurred_at 升序） */
fun entriesOnDate(all: List<MoodEntryEntity>, dateKey: String, zoneId: ZoneId = ZoneId.systemDefault()): List<MoodEntryEntity> {
    return all
        .filter { moodEntryDateKey(it.occurredAt, zoneId) == dateKey }
        .sortedBy { it.occurredAt }
}

/** 某条记录在当日的序号（编辑态） */
fun getDailyEntryIndex(
    all: List<MoodEntryEntity>,
    entryId: Long,
    dateKey: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): DailyEntryIndexMeta? {
    val day = entriesOnDate(all, dateKey, zoneId)
    val idx = day.indexOfFirst { it.id == entryId }
    if (idx < 0) return null
    return DailyEntryIndexMeta(index = idx + 1, total = day.size)
}

/** 新建下一条：当日已有 n 条则新记录为第 n+1 条 */
fun getNextDailyEntryIndex(
    all: List<MoodEntryEntity>,
    dateKey: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): DailyEntryIndexMeta {
    val total = entriesOnDate(all, dateKey, zoneId).size
    return DailyEntryIndexMeta(index = total + 1, total = total + 1)
}

/** 为历史列表批量计算同日序号 */
fun buildDailyIndexMap(
    all: List<MoodEntryEntity>,
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<Long, DailyEntryIndexMeta> {
    val byDate = all.groupBy { moodEntryDateKey(it.occurredAt, zoneId) }
    val out = mutableMapOf<Long, DailyEntryIndexMeta>()
    for (list in byDate.values) {
        val sorted = list.sortedBy { it.occurredAt }
        val total = sorted.size
        sorted.forEachIndexed { i, entry ->
            out[entry.id] = DailyEntryIndexMeta(index = i + 1, total = total)
        }
    }
    return out
}

fun formatDailyIndexLabel(meta: DailyEntryIndexMeta, forEdit: Boolean = false): String {
    return if (forEdit && meta.total > 1) {
        "今日第 ${meta.index} 条（共 ${meta.total} 条）"
    } else {
        "今日第 ${meta.index} 条"
    }
}

fun formatHistoryDailyIndexShort(meta: DailyEntryIndexMeta): String {
    return if (meta.total > 1) " (${meta.index}/${meta.total})" else ""
}
