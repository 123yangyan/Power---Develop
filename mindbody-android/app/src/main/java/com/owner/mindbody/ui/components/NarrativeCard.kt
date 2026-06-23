package com.owner.mindbody.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * 组件 A：叙事流体卡片 (Narrative Card)
 *
 * 职责：展示 LLM 身心反馈、状态判词、今日情绪日记等文字内容。
 * 禁区：绝对不在此卡片中放折线图或数据网格。
 *
 * @param accentColor  状态主题色，用于顶部 Badge 和微型装饰点
 * @param badgeLabel   顶部左上角状态 Badge 文字（如 "平静" / "正在分析"）
 * @param content      正文内容 Composable（行高已在默认排版中设为 24sp）
 */
@Composable
fun NarrativeCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MindBodyColors.CalmTeal,
    badgeLabel: String = "",
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = MindBodyShapes.NarrativeCard,
                ambientColor = MindBodyColors.AmbientShadow,
                spotColor = MindBodyColors.AmbientShadow
            )
            .clip(MindBodyShapes.NarrativeCard)
            .background(MindBodyColors.CardWhite)
            .padding(20.dp)
    ) {
        Column {
            if (badgeLabel.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 微型彩色装饰点
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(MindBodyShapes.Badge)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(MindBodyShapes.Badge)
                            .background(accentColor.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = StatLabel.copy(
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            content()
        }
    }
}

/** 叙事卡片内正文段落，行高 24sp，无衬线，营造"读信"体验。 */
@Composable
fun NarrativeBody(
    text: String,
    color: Color = MindBodyColors.OnBackground,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = color,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        modifier = modifier
    )
}

/** 叙事卡片内次级说明文字。 */
@Composable
fun NarrativeCaption(
    text: String,
    color: Color = MindBodyColors.OnBackgroundSecondary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = color,
            lineHeight = 18.sp
        ),
        modifier = modifier
    )
}
