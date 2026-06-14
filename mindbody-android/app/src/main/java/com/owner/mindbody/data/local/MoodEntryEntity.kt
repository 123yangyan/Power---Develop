package com.owner.mindbody.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 心情记录实体，对齐 emotion EntryRow 核心字段。
 * coordX：价值感 -4~+4；coordY：耗能度 -4~+4。
 */
@Entity(
    tableName = "mood_entries",
    indices = [
        Index("occurredAt"),
        Index("syncState"),
        Index(value = ["occurredAt", "syncState"])
    ]
)
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 日记正文 */
    val fact: String,
    /** 价值感坐标 -4（排斥）~ +4（愉悦） */
    val coordX: Int,
    /** 耗能度坐标 -4（轻松）~ +4（高耗能） */
    val coordY: Int,
    /** 记录发生时间戳（毫秒） */
    val occurredAt: Long,
    /** 保存时刻关联的心率估计值（±5 分钟窗口或短连接快照） */
    val hrAtEntry: Int? = null,
    /** 情绪角色 ID（如 role_flow）；与 coordX/coordY 静默映射，向后兼容旧记录 */
    val roleId: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)
