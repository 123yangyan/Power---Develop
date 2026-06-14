package com.owner.mindbody.ui.mood

import com.owner.mindbody.data.local.MoodEntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 历史列表单行展示数据，对齐 emotion historyRowPreview（简化版，无 legacy 字段） */
data class MoodHistoryRowView(
    val id: Long,
    val time: String,
    val dateKey: String,
    val dateLabel: String,
    val quadrantLabel: String,
    val coordX: Int,
    val coordY: Int,
    val intensity: Int,
    val polarity: MoodPolarity,
    val roleId: String?,
    val roleDisplayName: String?,
    val diaryBody: String,
    val isAvoidance: Boolean,
    val hrLabel: String?
)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateLabelFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")

fun buildHistoryRowView(
    entry: MoodEntryEntity,
    zoneId: ZoneId = ZoneId.systemDefault()
): MoodHistoryRowView {
    val zdt = Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId)
    val avoidance = isAvoidanceEntry(entry.fact)
    val role = EmotionRoles.findById(entry.roleId)
    return MoodHistoryRowView(
        id = entry.id,
        time = timeFormatter.format(zdt),
        dateKey = moodEntryDateKey(entry.occurredAt, zoneId),
        dateLabel = dateLabelFormatter.format(zdt),
        quadrantLabel = role?.displayName ?: getQuadrantLabel(entry.coordX, entry.coordY),
        coordX = entry.coordX,
        coordY = entry.coordY,
        intensity = coordIntensity(entry.coordY),
        polarity = moodPolarity(entry.coordX, entry.coordY),
        roleId = entry.roleId,
        roleDisplayName = role?.displayName,
        diaryBody = if (avoidance) entry.fact else entry.fact.ifBlank { "" },
        isAvoidance = avoidance,
        hrLabel = entry.hrAtEntry?.let { "$it BPM（估计关联）" }
    )
}

fun buildHistoryRows(
    entries: List<MoodEntryEntity>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<MoodHistoryRowView> {
    return entries.map { buildHistoryRowView(it, zoneId) }
}

fun formatDiaryDateLabel(dateKey: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = java.time.LocalDate.parse(dateKey, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    return dateLabelFormatter.format(date)
}
