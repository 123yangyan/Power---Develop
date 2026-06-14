package com.owner.mindbody.ui.mood

import androidx.annotation.DrawableRes
import com.owner.mindbody.R

/** 角色分组：皮克斯主线 vs 职场扩展 */
enum class EmotionRoleGroup {
    /** 《头脑特工队》皮克斯角色 */
    PIXAR,
    /** 心流/内耗等扩展角色 */
    EXTENDED
}

/**
 * 情绪角色（内在剧场）定义。
 * UI 展示角色图标，写入数据库时静默映射为 coordX / coordY 坐标。
 */
data class EmotionRole(
    val id: String,
    val displayName: String,
    val coordX: Int,
    val coordY: Int,
    val group: EmotionRoleGroup,
    @param:DrawableRes val iconRes: Int? = null,
    val accentColorHex: Long = 0xFF6366F1
)

object EmotionRoles {

    // —— 皮克斯《头脑特工队》主线 ——
    val Joy = EmotionRole("role_joy", "乐乐", 3, 2, EmotionRoleGroup.PIXAR, R.drawable.role_joy, 0xFFFBBF24)
    val Sadness = EmotionRole("role_sadness", "忧忧", -3, -2, EmotionRoleGroup.PIXAR, R.drawable.role_sadness, 0xFF60A5FA)
    val Anger = EmotionRole("role_anger", "怒怒", -3, 3, EmotionRoleGroup.PIXAR, R.drawable.role_anger, 0xFFEF4444)
    val Fear = EmotionRole("role_fear", "怕怕", -2, 3, EmotionRoleGroup.PIXAR, R.drawable.role_fear, 0xFF8B5CF6)
    val Disgust = EmotionRole("role_disgust", "厌厌", -2, 1, EmotionRoleGroup.PIXAR, R.drawable.role_disgust, 0xFF84CC16)
    val Anxiety = EmotionRole("role_anxiety", "焦焦", -2, 4, EmotionRoleGroup.PIXAR, R.drawable.role_anxiety, 0xFFF97316)
    val Embarrassment = EmotionRole("role_embarrassment", "尬尬", -2, 2, EmotionRoleGroup.PIXAR, R.drawable.role_embarrassment, 0xFFF472B6)
    val Ennui = EmotionRole("role_ennui", "丧丧", -3, -1, EmotionRoleGroup.PIXAR, R.drawable.role_ennui, 0xFF64748B)
    val Envy = EmotionRole("role_envy", "慕慕", -1, 3, EmotionRoleGroup.PIXAR, R.drawable.role_envy, 0xFF10B981)

    // —— 职场扩展角色 ——
    val Flow = EmotionRole("role_flow", "心流", 3, -2, EmotionRoleGroup.EXTENDED, R.drawable.role_flow, 0xFF34D399)
    val Rumination = EmotionRole("role_rumination", "内耗", -3, 4, EmotionRoleGroup.EXTENDED, R.drawable.role_rumination, 0xFF78716C)
    val Mechanical = EmotionRole("role_mechanical", "麻木", -2, -2, EmotionRoleGroup.EXTENDED, R.drawable.role_mechanical, 0xFF94A3B8)
    val Pride = EmotionRole("role_pride", "自豪", 4, 1, EmotionRoleGroup.EXTENDED, R.drawable.role_pride, 0xFFF59E0B)
    val Doubt = EmotionRole("role_doubt", "怀疑", -1, 2, EmotionRoleGroup.EXTENDED, R.drawable.role_doubt, 0xFFA78BFA)
    val Guilt = EmotionRole("role_guilt", "内疚", -2, -1, EmotionRoleGroup.EXTENDED, R.drawable.role_guilt, 0xFF059669)
    val Confusion = EmotionRole("role_confusion", "迷茫", -1, 0, EmotionRoleGroup.EXTENDED, R.drawable.role_confusion, 0xFFCBD5E1)
    val Inspiration = EmotionRole("role_inspiration", "顿悟", 2, 1, EmotionRoleGroup.EXTENDED, R.drawable.role_inspiration, 0xFFFDE047)
    val Calm = EmotionRole("role_calm", "平静", 2, -3, EmotionRoleGroup.EXTENDED, R.drawable.role_calm, 0xFF38BDF8)

    /** 皮克斯主线（探查弹窗首发阵容来源） */
    val pixarLineup: List<EmotionRole> = listOf(
        Joy, Sadness, Anger, Fear, Disgust, Anxiety, Embarrassment, Ennui, Envy
    )

    /** 职场扩展（探查弹窗「更多」优先展示） */
    val extendedLineup: List<EmotionRole> = listOf(
        Flow, Rumination, Mechanical, Pride, Doubt, Guilt, Confusion, Inspiration, Calm
    )

    /** 全部角色：皮克斯在前，扩展在后 */
    val all: List<EmotionRole> = pixarLineup + extendedLineup

    private val byId: Map<String, EmotionRole> = all.associateBy { it.id }

    /** 旧版 role_shame 兼容映射到尬尬 */
    private val legacyIdMap = mapOf("role_shame" to Embarrassment)

    fun findById(id: String?): EmotionRole? {
        if (id == null) return null
        return byId[id] ?: legacyIdMap[id]
    }

    /** 探查弹窗首发：皮克斯全线 9 人 */
    fun priorityDock(): List<EmotionRole> = pixarLineup

    /** 「更多」泊位：仅职场扩展 9 人（皮克斯已在首发） */
    fun dockOverflow(priority: List<EmotionRole>): List<EmotionRole> {
        val priorityIds = priority.map { it.id }.toSet()
        return extendedLineup.filter { it.id !in priorityIds }
    }

    /** 主动记录页场景 A 首发 4 人（心流 / 内耗 / 麻木 / 焦焦） */
    fun recordPageStageLineup(): List<EmotionRole> = listOf(Flow, Rumination, Mechanical, Anxiety)

    /** 主动记录页「展开全部」：除首发 4 人外的其余角色 */
    fun recordPageStageOverflow(): List<EmotionRole> {
        val stageIds = recordPageStageLineup().map { it.id }.toSet()
        return all.filter { it.id !in stageIds }
    }
}
