package com.owner.mindbody.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MindBodySplineChart(
    state: MindBodyChartState,
    restingBpm: Int = 70,
    onPresetSelected: (ChartWindowPreset) -> Unit,
    onWindowPan: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHr by remember { mutableStateOf(true) }
    var showTemp by remember { mutableStateOf(true) }
    var showHrv by remember { mutableStateOf(true) }
    var scrubberTimestamp by remember { mutableStateOf<Long?>(null) }

    val window = state.window
    val hrPoints = SplineChartUtils.filterValuePointsInWindow(state.hrPoints, window)
    val tempPoints = SplineChartUtils.filterValuePointsInWindow(state.tempPoints, window)
    val hrvPoints = SplineChartUtils.filterValuePointsInWindow(state.hrvPoints, window)
    val activityPoints = SplineChartUtils.filterValuePointsInWindow(state.activityPoints, window)
    val bpmRange = SplineChartUtils.computeValueBpmRange(hrPoints, restingBpm)
    val tempRange = SplineChartUtils.computeValueRange(tempPoints, 30f, 40f, 0.2f)
    val hrvRange = SplineChartUtils.computeValueRange(hrvPoints, 0f, 120f, 5f)
    val activityRange = SplineChartUtils.computeValueRange(activityPoints, 0f, 8f, 0.5f)
    val timeLabels = SplineChartUtils.formatTimeLabels(window)
    val hasVisibleData = hrPoints.isNotEmpty() || tempPoints.isNotEmpty() || hrvPoints.isNotEmpty()

    LaunchedEffect(window.startMs, window.endMs) {
        scrubberTimestamp = scrubberTimestamp?.coerceIn(window.startMs, window.endMs)
    }

    Column(modifier = modifier) {
        ChartHeader(
            preset = state.preset,
            showHr = showHr,
            showTemp = showTemp,
            showHrv = showHrv,
            onHrToggle = { showHr = !showHr },
            onTempToggle = { showTemp = !showTemp },
            onHrvToggle = { showHrv = !showHrv },
            onPresetSelected = onPresetSelected
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(245.dp)
                .padding(top = 8.dp)
                .clip(MindBodyShapes.StatCell)
                .background(
                    Brush.verticalGradient(
                        listOf(MindBodyColors.Background, MindBodyColors.Background.copy(alpha = 0.2f))
                    )
                )
                .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f), MindBodyShapes.StatCell)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .pointerInput(window) {
                        detectTapGestures { offset ->
                            scrubberTimestamp = SplineChartUtils.xToTimestamp(
                                offset.x,
                                size.width.toFloat(),
                                window
                            )
                        }
                    }
                    .pointerInput(window) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                scrubberTimestamp = SplineChartUtils.xToTimestamp(
                                    offset.x,
                                    size.width.toFloat(),
                                    window
                                )
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scrubberTimestamp = SplineChartUtils.xToTimestamp(
                                    change.position.x,
                                    size.width.toFloat(),
                                    window
                                )
                                val deltaMs = (-dragAmount.x / size.width.coerceAtLeast(1) * window.spanMs).toLong()
                                if (deltaMs != 0L) onWindowPan(deltaMs)
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val bottomPadding = 28f

                for (i in 1..4) {
                    val y = height * i / 5
                    drawLine(
                        color = MindBodyColors.PrimaryIndigo.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                state.exerciseBands
                    .filter { it.endMs >= window.startMs && it.startMs <= window.endMs }
                    .forEach { band ->
                        val left = SplineChartUtils.timestampToX(band.startMs, width, window)
                        val right = SplineChartUtils.timestampToX(band.endMs, width, window)
                        drawRect(
                            color = MindBodyColors.Emerald.copy(alpha = 0.08f),
                            topLeft = Offset(left, 0f),
                            size = Size((right - left).coerceAtLeast(2f), height - bottomPadding)
                        )
                    }

                drawActivityBars(activityPoints, activityRange, width, height, window)

                val restingY = SplineChartUtils.bpmToY(restingBpm.toFloat(), height, bpmRange, bottomPadding)
                if (restingY in 0f..height) {
                    drawLine(
                        color = MindBodyColors.Emerald.copy(alpha = 0.55f),
                        start = Offset(0f, restingY),
                        end = Offset(width, restingY),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                if (showHr) {
                    drawChartSeries(
                        points = hrPoints,
                        window = window,
                        color = MindBodyColors.HeartRed,
                        width = width,
                        height = height,
                        bottomPadding = bottomPadding,
                        valueToY = { value -> SplineChartUtils.bpmToY(value, height, bpmRange, bottomPadding) },
                        fill = true
                    )
                }
                if (showTemp) {
                    drawChartSeries(
                        points = tempPoints,
                        window = window,
                        color = MindBodyColors.Amber,
                        width = width,
                        height = height,
                        bottomPadding = bottomPadding,
                        valueToY = { value -> SplineChartUtils.valueToY(value, height, tempRange, bottomPadding) }
                    )
                }
                if (showHrv) {
                    drawChartSeries(
                        points = hrvPoints,
                        window = window,
                        color = MindBodyColors.PrimaryIndigo,
                        width = width,
                        height = height,
                        bottomPadding = bottomPadding,
                        valueToY = { value -> SplineChartUtils.normalizedToY(value, height, hrvRange, bottomPadding) }
                    )
                }

                scrubberTimestamp?.let { timestamp ->
                    val scrubberX = SplineChartUtils.timestampToX(timestamp, width, window)
                    drawLine(
                        color = MindBodyColors.OnBackground.copy(alpha = 0.35f),
                        start = Offset(scrubberX, 0f),
                        end = Offset(scrubberX, height - bottomPadding),
                        strokeWidth = 1.5f
                    )
                    drawScrubberDot(hrPoints, timestamp, scrubberX, height, bpmRange, bottomPadding, MindBodyColors.HeartRed)
                    drawScrubberDot(tempPoints, timestamp, scrubberX, height, tempRange, bottomPadding, MindBodyColors.Amber)
                    drawScrubberDot(hrvPoints, timestamp, scrubberX, height, hrvRange, bottomPadding, MindBodyColors.PrimaryIndigo)
                }
            }

            scrubberTimestamp?.let { timestamp ->
                ScrubberTooltip(
                    timestamp = timestamp,
                    hr = SplineChartUtils.findNearestPoint(hrPoints, timestamp)?.value,
                    temp = SplineChartUtils.findNearestPoint(tempPoints, timestamp)?.value,
                    hrv = SplineChartUtils.findNearestPoint(hrvPoints, timestamp)?.value,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeLabels.forEach { label ->
                    Text(text = label, style = StatLabel)
                }
            }

            if (!hasVisibleData) {
                Text(
                    text = "暂无曲线数据；连接 Loop 后会显示心率、体温与 HRV",
                    style = StatLabel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }

        Text(
            text = "${state.preset.label} 视窗 · ${state.preset.bucketMs / 1000}s 聚合 · 左右拖动查看今日历史",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ChartHeader(
    preset: ChartWindowPreset,
    showHr: Boolean,
    showTemp: Boolean,
    showHrv: Boolean,
    onHrToggle: () -> Unit,
    onTempToggle: () -> Unit,
    onHrvToggle: () -> Unit,
    onPresetSelected: (ChartWindowPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "身心交织曲线", style = CardTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartWindowPreset.entries.forEach { item ->
                    ToggleChip(
                        label = item.label,
                        color = MindBodyColors.PrimaryIndigo,
                        selected = preset == item,
                        onClick = { onPresetSelected(item) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("心率", MindBodyColors.HeartRed, showHr, onHrToggle)
            ToggleChip("体温", MindBodyColors.Amber, showTemp, onTempToggle)
            ToggleChip("HRV", MindBodyColors.PrimaryIndigo, showHrv, onHrvToggle)
            LegendDot(color = MindBodyColors.Emerald, label = "运动", line = false)
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MindBodyShapes.Badge)
            .background(color.copy(alpha = if (selected) 0.14f else 0.05f))
            .border(1.dp, color.copy(alpha = if (selected) 0.35f else 0.08f), MindBodyShapes.Badge)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = StatLabel.copy(color = if (selected) color else MindBodyColors.OnBackgroundSecondary)
        )
    }
}

@Composable
private fun ScrubberTooltip(
    timestamp: Long,
    hr: Float?,
    temp: Float?,
    hrv: Float?,
    modifier: Modifier = Modifier
) {
    val text = buildString {
        append(formatTimestamp(timestamp))
        append(" · HR ")
        append(hr?.let { "${it.toInt()} BPM" } ?: "--")
        append(" · 温 ")
        append(temp?.let { "%.1f°C".format(it) } ?: "--")
        append(" · RMSSD ")
        append(hrv?.let { "${it.toInt()} ms" } ?: "--")
    }
    Box(
        modifier = modifier
            .clip(MindBodyShapes.Badge)
            .background(MindBodyColors.CardSurfaceSolid)
            .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f), MindBodyShapes.Badge)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = StatLabel.copy(color = MindBodyColors.OnBackground))
    }
}

private fun DrawScope.drawChartSeries(
    points: List<ChartValuePoint>,
    window: ChartTimeWindow,
    color: Color,
    width: Float,
    height: Float,
    bottomPadding: Float,
    valueToY: (Float) -> Float,
    fill: Boolean = false
) {
    if (points.size >= 2) {
        val pixelPoints = points.map {
            Offset(
                x = SplineChartUtils.timestampToX(it.timestampMs, width, window),
                y = valueToY(it.value)
            )
        }
        if (fill) {
            drawPath(
                path = SplineChartUtils.buildSplinePath(pixelPoints, closeBottom = true, height = height - bottomPadding),
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = height
                )
            )
        }
        drawPath(
            path = SplineChartUtils.buildSplinePath(pixelPoints),
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    } else if (points.size == 1) {
        drawCircle(
            color = color,
            radius = 4f,
            center = Offset(
                SplineChartUtils.timestampToX(points.first().timestampMs, width, window),
                valueToY(points.first().value)
            )
        )
    }
}

private fun DrawScope.drawActivityBars(
    points: List<ChartValuePoint>,
    range: ChartValueRange,
    width: Float,
    height: Float,
    window: ChartTimeWindow
) {
    points.forEach { point ->
        val x = SplineChartUtils.timestampToX(point.timestampMs, width, window)
        val normalized = ((point.value - range.min) / range.span).coerceIn(0f, 1f)
        val barHeight = 4f + normalized * 20f
        drawRect(
            color = MindBodyColors.Emerald.copy(alpha = 0.35f),
            topLeft = Offset(x, height - 26f - barHeight),
            size = Size(3f, barHeight)
        )
    }
}

private fun DrawScope.drawScrubberDot(
    points: List<ChartValuePoint>,
    timestamp: Long,
    x: Float,
    height: Float,
    range: BpmRange,
    bottomPadding: Float,
    color: Color
) {
    val nearest = SplineChartUtils.findNearestPoint(points, timestamp) ?: return
    drawCircle(color = color, radius = 4.5f, center = Offset(x, SplineChartUtils.bpmToY(nearest.value, height, range, bottomPadding)))
}

private fun DrawScope.drawScrubberDot(
    points: List<ChartValuePoint>,
    timestamp: Long,
    x: Float,
    height: Float,
    range: ChartValueRange,
    bottomPadding: Float,
    color: Color
) {
    val nearest = SplineChartUtils.findNearestPoint(points, timestamp) ?: return
    drawCircle(color = color, radius = 4.5f, center = Offset(x, SplineChartUtils.valueToY(nearest.value, height, range, bottomPadding)))
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    line: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = if (line) 10.dp else 8.dp, height = if (line) 4.dp else 8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary))
    }
}

private val tooltipFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(tooltipFormatter)
}
