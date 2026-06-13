package com.owner.mindbody.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.owner.mindbody.data.local.HrSampleEntity
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

@Composable
fun MindBodySplineChart(
    samples: List<HrSampleEntity>,
    restingBpm: Int = 70,
    modifier: Modifier = Modifier
) {
    val chartPoints = SplineChartUtils.downsampleByTime(samples)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "身心交织曲线", style = CardTitle)
                Box(
                    modifier = Modifier
                        .clip(MindBodyShapes.Badge)
                        .background(MindBodyColors.PrimaryIndigoLight.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "贝塞尔拟合",
                        style = StatLabel.copy(color = MindBodyColors.PrimaryIndigo)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot(color = MindBodyColors.HeartRed, label = "生理心率", line = true)
                LegendDot(color = MindBodyColors.Amber, label = "心情打点", line = false, dimmed = true)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .padding(top = 8.dp)
                .clip(MindBodyShapes.StatCell)
                .background(
                    Brush.verticalGradient(
                        listOf(MindBodyColors.Background, MindBodyColors.Background.copy(alpha = 0.2f))
                    )
                )
                .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f), MindBodyShapes.StatCell)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val width = size.width
                val height = size.height
                val restingY = SplineChartUtils.bpmToY(restingBpm.toFloat(), height)

                // 背景网格线
                val gridLines = 4
                for (i in 1..gridLines) {
                    val y = height * i / (gridLines + 1)
                    drawLine(
                        color = MindBodyColors.PrimaryIndigo.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // 静息绿区
                drawRect(
                    color = MindBodyColors.Emerald.copy(alpha = 0.05f),
                    topLeft = Offset(0f, restingY),
                    size = androidx.compose.ui.geometry.Size(width, height - restingY)
                )

                // 静息虚线
                drawLine(
                    color = MindBodyColors.Emerald.copy(alpha = 0.6f),
                    start = Offset(0f, restingY),
                    end = Offset(width, restingY),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )

                if (chartPoints.size >= 2) {
                    val pixelPoints = chartPoints.map { pt ->
                        Offset(
                            x = SplineChartUtils.minutesToX(pt.minutesOfDay, width),
                            y = SplineChartUtils.bpmToY(pt.bpm.toFloat(), height)
                        )
                    }

                    val areaPath = SplineChartUtils.buildSplinePath(
                        points = pixelPoints,
                        closeBottom = true,
                        height = height
                    )
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MindBodyColors.HeartRed.copy(alpha = 0.18f),
                                MindBodyColors.HeartRed.copy(alpha = 0f)
                            ),
                            startY = 0f,
                            endY = height
                        )
                    )

                    val strokePath = SplineChartUtils.buildSplinePath(pixelPoints)
                    drawPath(
                        path = strokePath,
                        color = MindBodyColors.HeartRed,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                } else if (chartPoints.size == 1) {
                    val pt = chartPoints.first()
                    drawCircle(
                        color = MindBodyColors.HeartRed,
                        radius = 4f,
                        center = Offset(
                            SplineChartUtils.minutesToX(pt.minutesOfDay, width),
                            SplineChartUtils.bpmToY(pt.bpm.toFloat(), height)
                        )
                    )
                }
            }

            // 静息基准标签
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, top = 90.dp)
                    .clip(MindBodyShapes.Badge)
                    .background(MindBodyColors.EmeraldSurface)
                    .border(1.dp, MindBodyColors.Emerald.copy(alpha = 0.2f), MindBodyShapes.Badge)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "静息基准 $restingBpm BPM",
                    style = StatLabel.copy(color = MindBodyColors.Emerald)
                )
            }

            // 时间刻度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("08:00", "12:00", "16:00", "20:00", "24:00").forEach { label ->
                    Text(text = label, style = StatLabel)
                }
            }

            if (chartPoints.isEmpty()) {
                Text(
                    text = "暂无数据，请先在「设备」页连接 Polar Loop",
                    style = StatLabel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }

        Text(
            text = "心情打点 · Phase 2 即将推出",
            style = StatLabel.copy(color = MindBodyColors.Amber.copy(alpha = 0.8f)),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun LegendDot(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    line: Boolean,
    dimmed: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (line) {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (dimmed) 0.4f else 1f))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (dimmed) 0.4f else 1f))
            )
        }
        Text(
            text = label,
            style = StatLabel.copy(
                color = MindBodyColors.OnBackgroundSecondary.copy(alpha = if (dimmed) 0.6f else 1f)
            )
        )
    }
}
