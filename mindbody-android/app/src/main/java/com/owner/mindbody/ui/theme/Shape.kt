package com.owner.mindbody.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object MindBodyShapes {
    // 叙事级：LLM 反馈、日记、状态诊断
    val NarrativeCard = RoundedCornerShape(32.dp)
    // 数据级：HRV 指标、设备状态
    val DataCard = RoundedCornerShape(24.dp)
    // Hero 顶部锚点
    val HeroCard = RoundedCornerShape(32.dp)
    // 全圆角按钮与徽章
    val Button = CircleShape
    val Badge = RoundedCornerShape(50)

    // 保持兼容性的别名
    val PremiumCard = RoundedCornerShape(28.dp)
    val PremiumCardLarge = RoundedCornerShape(32.dp)
    val StatCell = RoundedCornerShape(16.dp)
    val NavIsland = RoundedCornerShape(50)
    val NavPill = RoundedCornerShape(50)
    val Capsule = RoundedCornerShape(50)
    val RadioOption = RoundedCornerShape(16.dp)
}
