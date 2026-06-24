package com.owner.mindbody.ui.developer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.stream.AttemptResult
import com.owner.mindbody.data.stream.PpiWindowAttempt
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.components.StatGrid
import com.owner.mindbody.ui.components.StatItem
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue

@Composable
fun PpiUploadLogScreen(
    onBack: () -> Unit,
    viewModel: PpiUploadLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MindBodyColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MindBodyColors.OnBackground
                    )
                }
                SectionHeader(
                    eyebrow = "DEVELOPER",
                    title = "PPI 上传日志",
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (summary.totalCount > 0) {
                    item {
                        StatGrid(
                            items = listOf(
                                StatItem(
                                    label = "全部",
                                    value = summary.totalCount.toString(),
                                ),
                                StatItem(
                                    label = "已上传",
                                    value = summary.acceptedCount.toString(),
                                    valueColor = MindBodyColors.Emerald,
                                ),
                                StatItem(
                                    label = "未上传",
                                    value = summary.skippedCount.toString(),
                                    valueColor = MindBodyColors.Amber,
                                ),
                                StatItem(
                                    label = "上传率",
                                    value = "${"%.0f".format(summary.uploadRatePct)}%",
                                    valueColor = MindBodyColors.PrimaryIndigo,
                                ),
                            )
                        )
                    }

                    if (summary.skippedCount > 0 && summary.skipBreakdown.isNotEmpty()) {
                        item {
                            SkipBreakdownSection(
                                skippedCount = summary.skippedCount,
                                breakdown = summary.skipBreakdown,
                            )
                        }
                    }

                    if (summary.recentTimeline.isNotEmpty()) {
                        item {
                            TimelineBarSection(timeline = summary.recentTimeline)
                        }
                    }

                    if (summary.avgCoveragePct > 0f) {
                        item {
                            Text(
                                text = "平均覆盖率 ${"%.1f".format(summary.avgCoveragePct)}% · " +
                                    "失败 ${summary.failedCount} 次",
                                style = StatLabel,
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.copyAll(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                            shape = MindBodyShapes.RadioOption
                        ) {
                            Text("复制 JSON")
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearLogs() },
                            modifier = Modifier.weight(1f),
                            shape = MindBodyShapes.RadioOption
                        ) {
                            Text("清空")
                        }
                    }
                }

                item {
                    Text(
                        text = "共 ${entries.size} 条 · 最新在上 · 每 ~90s 一次窗口尝试",
                        style = StatLabel
                    )
                }

                if (entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MindBodyColors.StatCellBg, MindBodyShapes.StatCell)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "暂无记录。连接手环并开启云端同步后，此处会显示每次 PPI 窗口上传尝试。",
                                style = StatLabel
                            )
                        }
                    }
                } else {
                    items(entries.asReversed(), key = { it.id }) { attempt ->
                        SelectionContainer {
                            Text(
                                text = attempt.formatLine(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = resultColor(attempt.result),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MindBodyShapes.StatCell)
                                    .background(MindBodyColors.StatCellBg)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipBreakdownSection(
    skippedCount: Int,
    breakdown: List<SkipBreakdownItem>,
) {
    val maxCount = breakdown.maxOfOrNull { it.count } ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.DataCard)
            .background(MindBodyColors.CardWhite)
            .border(1.dp, MindBodyColors.StatCellBorder, MindBodyShapes.DataCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SKIP 原因分布（未上传 $skippedCount 次）",
            style = CardTitle
        )
        breakdown.forEach { item ->
            SkipBreakdownRow(
                label = item.reason.label(),
                count = item.count,
                pct = item.pctOfSkipped,
                maxCount = maxCount,
            )
        }
    }
}

@Composable
private fun SkipBreakdownRow(
    label: String,
    count: Int,
    pct: Float,
    maxCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = StatLabel,
            modifier = Modifier.width(100.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(MindBodyShapes.StatCell)
                .background(MindBodyColors.StatCellBg)
        ) {
            val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (fraction > 0f) {
                    drawRoundRect(
                        color = MindBodyColors.Amber,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
        Text(
            text = "$count",
            style = StatValue.copy(fontSize = 12.sp),
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = "${"%.0f".format(pct)}%",
            style = StatLabel,
            modifier = Modifier.width(32.dp)
        )
    }
}

@Composable
private fun TimelineBarSection(timeline: List<AttemptResult>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.DataCard)
            .background(MindBodyColors.CardWhite)
            .border(1.dp, MindBodyColors.StatCellBorder, MindBodyShapes.DataCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "最近窗口时序（${timeline.size} 条 · 右=最新）",
            style = CardTitle
        )
        TimelineBar(timeline = timeline)
        TimelineLegend()
    }
}

@Composable
private fun TimelineBar(timeline: List<AttemptResult>) {
    val gapDp = 2.dp
    val barHeight = 28.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        if (timeline.isEmpty()) return@Canvas

        val gapPx = gapDp.toPx()
        val count = timeline.size
        val totalGap = gapPx * (count - 1).coerceAtLeast(0)
        val blockWidth = (size.width - totalGap) / count
        val blockHeight = size.height
        val corner = 3.dp.toPx()

        timeline.forEachIndexed { index, result ->
            val x = index * (blockWidth + gapPx)
            drawRoundRect(
                color = result.toComposeColor(),
                topLeft = Offset(x, 0f),
                size = Size(blockWidth, blockHeight),
                cornerRadius = CornerRadius(corner),
            )
        }
    }
}

@Composable
private fun TimelineLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(color = MindBodyColors.Emerald, label = "已上传")
        LegendItem(color = MindBodyColors.Amber, label = "未上传")
        LegendItem(color = MindBodyColors.HeartRed, label = "失败")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(MindBodyShapes.StatCell)
                .background(color)
        )
        Text(text = label, style = StatLabel)
    }
}

private fun AttemptResult.toComposeColor(): Color = when (this) {
    AttemptResult.ACCEPTED -> MindBodyColors.Emerald
    AttemptResult.SKIPPED -> MindBodyColors.Amber
    AttemptResult.FAILED -> MindBodyColors.HeartRed
}

@Composable
private fun resultColor(result: AttemptResult): Color = result.toComposeColor()
