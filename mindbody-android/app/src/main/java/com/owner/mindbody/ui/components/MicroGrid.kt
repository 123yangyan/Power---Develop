package com.owner.mindbody.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * 高密度数据网格中一格的数据模型。
 *
 * @param label         指标名称（如 "RMSSD"）
 * @param value         当前值字符串（如 "42"）
 * @param unit          单位（如 "ms"）
 * @param valueColor    数值颜色，默认 [MindBodyColors.OnBackground]
 * @param historyPoints 过去约 1h 的历史数据点，用于绘制 Sparkline；为空则不绘制
 * @param sparklineColor Sparkline 线条颜色
 */
data class MicroGridItem(
    val label: String,
    val value: String,
    val unit: String = "",
    val valueColor: Color = Color.Unspecified,
    val historyPoints: List<Float> = emptyList(),
    val sparklineColor: Color = Color.Unspecified
)

/**
 * 组件 B：高密度数据网格 (Micro Grid)
 *
 * 职责：收纳所有硬核生理指标。
 * - 2×2 或 2×3 布局，分割线 0.5dp Alpha 10%
 * - 每格底部可选 16dp Sparkline（无坐标轴）
 * - 背景纯白，圆角 24dp，极浅环境光阴影
 */
@Composable
fun MicroGrid(
    items: List<MicroGridItem>,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val rows = (items.size + columns - 1) / columns
    val dividerColor = Color.Black.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = MindBodyShapes.DataCard,
                ambientColor = MindBodyColors.AmbientShadow,
                spotColor = MindBodyColors.AmbientShadow
            )
            .clip(MindBodyShapes.DataCard)
            .background(MindBodyColors.CardWhite)
    ) {
        Column {
            repeat(rows) { rowIdx ->
                if (rowIdx > 0) {
                    HorizontalMicroDivider(color = dividerColor)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    repeat(columns) { colIdx ->
                        val itemIdx = rowIdx * columns + colIdx
                        if (colIdx > 0) {
                            VerticalMicroDivider(color = dividerColor)
                        }
                        if (itemIdx < items.size) {
                            MicroGridCell(
                                item = items[itemIdx],
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroGridCell(
    item: MicroGridItem,
    modifier: Modifier = Modifier
) {
    val resolvedValueColor = if (item.valueColor == Color.Unspecified)
        MindBodyColors.OnBackground else item.valueColor
    val resolvedSparkColor = if (item.sparklineColor == Color.Unspecified)
        MindBodyColors.CalmTeal else item.sparklineColor

    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = item.label,
            style = StatLabel.copy(
                fontSize = 10.sp,
                color = MindBodyColors.OnBackgroundSecondary,
                fontWeight = FontWeight.Medium
            )
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = item.value,
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = resolvedValueColor
                )
            )
            if (item.unit.isNotEmpty()) {
                Text(
                    text = " ${item.unit}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MindBodyColors.OnBackgroundSecondary
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        if (item.historyPoints.size >= 2) {
            Sparkline(
                points = item.historyPoints,
                color = resolvedSparkColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun Sparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.min()
        val maxVal = points.max()
        val range = (maxVal - minVal).takeIf { it > 0f } ?: 1f

        val path = Path()
        points.forEachIndexed { i, value ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - (size.height * (value - minVal) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun HorizontalMicroDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 0.5.dp.toPx()
        )
    }
}

@Composable
private fun VerticalMicroDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .width(0.5.dp)
            .height(72.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 0.5.dp.toPx()
        )
    }
}
