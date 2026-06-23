package com.owner.mindbody.ui.physio

import androidx.compose.ui.graphics.Color
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 生理状态 → 颜色 + 中文标签 的语义映射。
 * 所有使用状态色的组件（HeroIndicator、NarrativeCard）
 * 通过此映射获取颜色，确保全局一致。
 */
data class StateColorToken(
    val accentColor: Color,
    val surfaceColor: Color,
    val zhLabel: String,
    val description: String = ""
)

object StateColors {
    private val tokens = mapOf(
        "baseline_building" to StateColorToken(
            accentColor = MindBodyColors.OceanBlue,
            surfaceColor = MindBodyColors.BlueSurface,
            zhLabel = "正在建立基线",
            description = "系统正在收集您的个人生理基线数据"
        ),
        "calm" to StateColorToken(
            accentColor = MindBodyColors.CalmTeal,
            surfaceColor = MindBodyColors.TealSurface,
            zhLabel = "平静",
            description = "当前身心处于平和放松状态"
        ),
        "normal" to StateColorToken(
            accentColor = MindBodyColors.PrimaryIndigo,
            surfaceColor = MindBodyColors.PrimaryIndigoSurface,
            zhLabel = "正常",
            description = "生理指标处于正常范围内"
        ),
        "elevated" to StateColorToken(
            accentColor = MindBodyColors.StressAmber,
            surfaceColor = MindBodyColors.AmberSurface,
            zhLabel = "应激升高",
            description = "检测到轻度应激反应，建议适当休息"
        ),
        "anxious" to StateColorToken(
            accentColor = MindBodyColors.AnxietyRose,
            surfaceColor = MindBodyColors.RoseSurface,
            zhLabel = "疑似焦虑",
            description = "生理指标显示焦虑倾向，关注当下感受"
        ),
        "high_anxiety" to StateColorToken(
            accentColor = MindBodyColors.HighAlertRed,
            surfaceColor = MindBodyColors.AlertRedSurface,
            zhLabel = "高度焦虑",
            description = "建议暂停当前活动，进行深呼吸或休息"
        )
    )

    private val defaultToken = StateColorToken(
        accentColor = MindBodyColors.OceanBlue,
        surfaceColor = MindBodyColors.BlueSurface,
        zhLabel = "分析中",
        description = "正在分析生理数据"
    )

    fun of(stateLabel: String?): StateColorToken =
        tokens[stateLabel] ?: defaultToken

    // 对已知状态标签的只读枚举访问
    val baselineBuilding get() = tokens["baseline_building"]!!
    val calm get() = tokens["calm"]!!
    val normal get() = tokens["normal"]!!
    val elevated get() = tokens["elevated"]!!
    val anxious get() = tokens["anxious"]!!
    val highAnxiety get() = tokens["high_anxiety"]!!
}

