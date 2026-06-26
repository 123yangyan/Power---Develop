package com.owner.mindbody.ui.physio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.LlmFeedbackEntry
import com.owner.mindbody.data.PhysioStateSummary
import com.owner.mindbody.ui.components.HeroIndicator
import com.owner.mindbody.ui.components.MicroGrid
import com.owner.mindbody.ui.components.MicroGridItem
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCaption
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * 「状态」Tab 主屏幕。
 *
 * 页面组装规则：
 * - Head  (20%): HeroIndicator — 当前状态结论 + 焦虑分 + BPM 呼吸环
 * - Middle (50%): NarrativeCard — LLM 反馈正文，基线期替换为进度叙述卡
 * - Bottom (30%): MicroGrid — HRV 6格数据，供下滑查阅
 */
@Composable
fun PhysioStateScreen(
    onNavigateToFeedbackHistory: () -> Unit,
    viewModel: PhysioStateViewModel = viewModel()
) {
    val physioState by viewModel.latestPhysioState.collectAsState()
    val feedbackList by viewModel.feedbackHistory.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            eyebrow = "MINDBODY INSIGHT",
            title = "身心感知"
        )

        // ── HEAD (20%): Hero 状态指示器 ────────────────────────────────────
        PhysioHeroSection(physioState = physioState)

        // ── MIDDLE (50%): 叙事卡 ───────────────────────────────────────────
        if (physioState == null || physioState!!.stateLabel == "baseline_building") {
            BaselineNarrativeCard(windowCount = physioState?.baselineWindowCount ?: 0)
        } else {
            LlmNarrativeCard(physioState = physioState!!)
        }

        // ── BOTTOM (30%): HRV 数据网格 ────────────────────────────────────
        if (physioState != null && physioState!!.stateLabel != "baseline_building") {
            HrvMicroGridCard(physioState = physioState!!)
        }

        // ── 历史状态条形卡（下滑可见）────────────────────────────────────
        if (feedbackList.isNotEmpty()) {
            HistoryStripsSection(
                entries = feedbackList.take(5),
                totalCount = feedbackList.size,
                onNavigateToFeedbackHistory = onNavigateToFeedbackHistory
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Hero 区域 ─────────────────────────────────────────────────────────────────

@Composable
private fun PhysioHeroSection(physioState: PhysioStateSummary?) {
    val stateToken = StateColors.of(physioState?.stateLabel)
    val anxietyScore = physioState?.anxietyScore ?: 0f
    val animatedScore by animateFloatAsState(
        targetValue = anxietyScore,
        animationSpec = tween(durationMillis = 800),
        label = "anxietyScore"
    )

    val primaryLabel = when {
        physioState == null -> "--"
        physioState.stateLabel == "baseline_building" -> "${physioState.baselineWindowCount}"
        else -> "${animatedScore.toInt()}"
    }
    val trackLabel = when {
        physioState == null -> ""
        physioState.stateLabel == "baseline_building" -> "/ 50 窗口"
        else -> "焦虑指数"
    }
    val trackProgress = when {
        physioState == null -> 0f
        physioState.stateLabel == "baseline_building" ->
            (physioState.baselineWindowCount / 50f).coerceIn(0f, 1f)
        else -> anxietyScore / 100f
    }

    HeroIndicator(
        primaryLabel = primaryLabel,
        secondaryLabel = stateToken.zhLabel,
        accentColor = stateToken.accentColor,
        surfaceColor = stateToken.surfaceColor,
        trackProgress = trackProgress,
        trackLabel = trackLabel,
        height = 220.dp
    )
}

// ── 基线建立中叙事卡 ──────────────────────────────────────────────────────────

@Composable
private fun BaselineNarrativeCard(windowCount: Int) {
    val stateToken = StateColors.baselineBuilding
    val progress = (windowCount / 50f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "baselineProgress"
    )

    NarrativeCard(
        accentColor = stateToken.accentColor,
        badgeLabel = stateToken.zhLabel
    ) {
        NarrativeBody(
            text = if (windowCount == 0)
                "系统正在等待第一次心跳间期数据。请保持 Polar Loop 佩戴，并确保蓝牙连接正常。"
            else
                "正在建立您的专属生理基线。已收集 $windowCount 个 15 分钟分析窗口，目标 50 个。基线成熟后，将开始为您生成个性化状态评估。"
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MindBodyShapes.Badge),
            color = stateToken.accentColor,
            trackColor = stateToken.accentColor.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        NarrativeCaption(text = "已收集 $windowCount / 50 个分析窗口")
    }
}

// ── LLM 反馈叙事卡 ────────────────────────────────────────────────────────────

@Composable
private fun LlmNarrativeCard(physioState: PhysioStateSummary) {
    val stateToken = StateColors.of(physioState.stateLabel)

    NarrativeCard(
        accentColor = stateToken.accentColor,
        badgeLabel = stateToken.zhLabel
    ) {
        val analysisTs = physioState.lastStreamTs ?: physioState.timestampMs.takeIf { it > 0L }
        if (analysisTs != null) {
            AnalysisTimeRow(
                timestampMs = analysisTs,
                accentColor = stateToken.accentColor
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (physioState.hrSurgeFlag) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MindBodyShapes.DataCard)
                    .background(MindBodyColors.StressAmber.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚡ 检测到心率突升",
                    style = StatLabel.copy(
                        color = MindBodyColors.StressAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        NarrativeBody(
            text = physioState.llmMessage
                ?: stateToken.description.ifEmpty {
                    "暂无最新分析反馈。请保持设备佩戴，系统将在下一个分析窗口后更新。"
                }
        )
    }
}

// ── 分析时间行（叙事卡顶部）────────────────────────────────────────────────

@Composable
private fun AnalysisTimeRow(
    timestampMs: Long,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "🕐",
            fontSize = 15.sp
        )
        Text(
            text = "最近分析：${formatRelativeTime(timestampMs)}",
            style = StatLabel.copy(
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

// ── 历史状态条形卡区块 ────────────────────────────────────────────────────────

@Composable
private fun HistoryStripsSection(
    entries: List<LlmFeedbackEntry>,
    totalCount: Int,
    onNavigateToFeedbackHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史状态",
                style = CardTitle.copy(color = MindBodyColors.OnBackgroundSecondary)
            )
            TextButton(onClick = onNavigateToFeedbackHistory) {
                Text(
                    text = "查看全部 $totalCount 条 →",
                    style = StatLabel.copy(
                        color = MindBodyColors.CalmTeal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        entries.forEach { entry ->
            HistoryStripCard(entry = entry)
        }
    }
}

@Composable
private fun HistoryStripCard(entry: LlmFeedbackEntry) {
    val stateToken = StateColors.of(entry.stateLabel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.DataCard)
            .background(MindBodyColors.CardWhite)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(stateToken.accentColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stateToken.zhLabel,
                    style = StatLabel.copy(
                        color = stateToken.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = formatHistoryStripTime(entry.timestampMs),
                    style = StatLabel.copy(
                        color = MindBodyColors.OnBackgroundSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.stateLabel != "baseline_building") {
                    Text(
                        text = "焦虑 ${entry.anxietyScore.toInt()}",
                        style = StatLabel.copy(
                            color = stateToken.accentColor.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "·",
                        style = StatLabel.copy(
                            color = MindBodyColors.OnBackgroundSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
                Text(
                    text = entry.message,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = StatLabel.copy(
                        color = MindBodyColors.OnBackgroundSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

// ── HRV 数据网格 ──────────────────────────────────────────────────────────────

@Composable
private fun HrvMicroGridCard(physioState: PhysioStateSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "生理指标详情",
            style = CardTitle.copy(color = MindBodyColors.OnBackgroundSecondary)
        )
        MicroGrid(
            items = listOf(
                MicroGridItem(
                    label = "RMSSD",
                    value = physioState.rmssd?.let { "%.1f".format(it) } ?: "--",
                    unit = "ms",
                    valueColor = MindBodyColors.CalmTeal,
                    sparklineColor = MindBodyColors.CalmTeal
                ),
                MicroGridItem(
                    label = "SDNN",
                    value = physioState.sdnn?.let { "%.1f".format(it) } ?: "--",
                    unit = "ms",
                    valueColor = MindBodyColors.OceanBlue,
                    sparklineColor = MindBodyColors.OceanBlue
                ),
                MicroGridItem(
                    label = "LF/HF",
                    value = physioState.lfHf?.let { "%.2f".format(it) } ?: "--",
                    valueColor = MindBodyColors.StressAmber,
                    sparklineColor = MindBodyColors.StressAmber
                ),
                MicroGridItem(
                    label = "呼吸频率",
                    value = physioState.breathingRate?.let { "%.1f".format(it) } ?: "--",
                    unit = "次/分",
                    valueColor = com.owner.mindbody.ui.theme.MindBodyColors.PrimaryIndigo
                ),
                MicroGridItem(
                    label = "SampEn",
                    value = physioState.sampEn?.let { "%.2f".format(it) } ?: "--",
                    valueColor = com.owner.mindbody.ui.theme.MindBodyColors.Emerald
                ),
                MicroGridItem(
                    label = "DFA α1",
                    value = physioState.dfaAlpha1?.let { "%.2f".format(it) } ?: "--",
                    valueColor = MindBodyColors.OnBackgroundSecondary
                )
            ),
            columns = 2
        )
    }
}

// ── 工具函数 ──────────────────────────────────────────────────────────────────

private fun formatRelativeTime(tsMs: Long): String {
    val diffMs = System.currentTimeMillis() - tsMs
    return when {
        diffMs < 60_000L -> "刚刚"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} 分钟前"
        diffMs < 86_400_000L -> "${diffMs / 3_600_000L} 小时前"
        else -> "${diffMs / 86_400_000L} 天前"
    }
}

private fun formatHistoryStripTime(tsMs: Long): String {
    val diffMs = System.currentTimeMillis() - tsMs
    return when {
        diffMs < 60_000L -> "刚刚"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} 分钟前"
        diffMs < 86_400_000L -> "${diffMs / 3_600_000L} 小时前"
        diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000L} 天前"
        else -> {
            val date = java.util.Date(tsMs)
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(date)
        }
    }
}

