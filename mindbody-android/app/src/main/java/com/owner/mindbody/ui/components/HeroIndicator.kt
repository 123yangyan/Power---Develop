package com.owner.mindbody.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * 组件 C：动态状态环 / 英雄指示器
 *
 * 页面顶部视觉锚点（占视口约 20%）。
 * - 径向渐变底色，由 [accentColor] 决定当前状态基调
 * - 中心展示结论文字（[primaryLabel] + [secondaryLabel]）
 * - 外轨道呼吸动画，承载 [trackProgress]（0f–1f）和 [trackLabel]
 */
@Composable
fun HeroIndicator(
    primaryLabel: String,
    secondaryLabel: String,
    accentColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    trackProgress: Float = 0f,
    trackLabel: String = "",
    trackUnitLabel: String = "",
    height: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroBreath")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MindBodyShapes.HeroCard)
            .background(surfaceColor),
        contentAlignment = Alignment.Center
    ) {
        // 径向渐变背景光晕
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.18f * breathScale),
                        accentColor.copy(alpha = 0f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.width * 0.55f
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 外轨道进度环
            Box(contentAlignment = Alignment.Center) {
                TrackRing(
                    progress = trackProgress,
                    accentColor = accentColor,
                    breathAlpha = breathAlpha,
                    size = 140.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = primaryLabel,
                        style = TextStyle(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = MindBodyColors.OnBackground
                        )
                    )
                    if (trackLabel.isNotEmpty()) {
                        Text(
                            text = "$trackLabel $trackUnitLabel".trim(),
                            style = StatLabel.copy(color = accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 状态结论标签
            Box(
                modifier = Modifier
                    .clip(MindBodyShapes.Badge)
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = secondaryLabel,
                    style = StatLabel.copy(
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun TrackRing(
    progress: Float,
    accentColor: Color,
    breathAlpha: Float,
    size: Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 4.dp.toPx()
        val radius = (size.toPx() / 2f) - strokeWidth
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

        // 轨道背景
        drawCircle(
            color = accentColor.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 呼吸进度弧
        if (progress > 0f) {
            drawArc(
                color = accentColor.copy(alpha = breathAlpha),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
