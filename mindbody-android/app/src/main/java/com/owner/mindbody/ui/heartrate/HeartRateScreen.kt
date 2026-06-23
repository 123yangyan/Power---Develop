package com.owner.mindbody.ui.heartrate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.ui.components.ConnectionStatusCapsule
import com.owner.mindbody.ui.components.HeroIndicator
import com.owner.mindbody.ui.components.MicroGrid
import com.owner.mindbody.ui.components.MicroGridItem
import com.owner.mindbody.ui.components.MindBodySplineChart
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCaption
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.components.StreamStatusBadge
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * 心率页 — 重构为"身体陪伴日记"布局：
 * - Head  (20%): HeroIndicator（BPM 呼吸环 + 连接状态）
 * - Middle (50%): NarrativeCard（皮温 + 今日体感文字描述）
 * - Bottom (30%): MicroGrid（今日心率统计）+ 折叠 SplineChart
 */
@Composable
fun HeartRateScreen(
    viewModel: HeartRateViewModel = viewModel()
) {
    val currentHr by viewModel.currentHr.collectAsState()
    val currentSkinTemp by viewModel.currentSkinTemp.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val stats by viewModel.todayStats.collectAsState()
    val chartState by viewModel.chartState.collectAsState()
    var showChart by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.startBackgroundStream()
        onDispose { viewModel.stopBackgroundStream() }
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
            title = "实时心觉",
            trailing = {
                StreamStatusBadge(connected = connectionState == ConnectionState.CONNECTED)
            }
        )

        // ── HEAD (20%): BPM 呼吸环 Hero ─────────────────────────────────
        HeroIndicator(
            primaryLabel = currentHr?.toString() ?: "--",
            secondaryLabel = connectionLabel(connectionState),
            accentColor = MindBodyColors.HeartRed,
            surfaceColor = MindBodyColors.HeartRed.copy(alpha = 0.06f),
            trackProgress = currentHr?.let { (it / 200f).coerceIn(0f, 1f) } ?: 0f,
            trackLabel = currentHr?.let { "$it" } ?: "",
            trackUnitLabel = if (currentHr != null) "BPM" else "",
            height = 220.dp
        )

        // ── MIDDLE (50%): 皮温叙事卡 ─────────────────────────────────────
        SkinTemperatureNarrativeCard(
            tempCelsius = currentSkinTemp,
            connectionState = connectionState
        )

        // ── BOTTOM (30%): 今日统计 MicroGrid ─────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "今日生理负荷",
                style = CardTitle.copy(color = MindBodyColors.OnBackgroundSecondary)
            )
            MicroGrid(
                items = listOf(
                    MicroGridItem(
                        label = "样本数",
                        value = stats.count.toString(),
                        valueColor = MindBodyColors.OnBackground
                    ),
                    MicroGridItem(
                        label = "平均心率",
                        value = stats.average?.toString() ?: "--",
                        unit = "BPM",
                        valueColor = MindBodyColors.PrimaryIndigo
                    ),
                    MicroGridItem(
                        label = "最高心率",
                        value = stats.max?.toString() ?: "--",
                        unit = "BPM",
                        valueColor = MindBodyColors.HeartRed
                    ),
                    MicroGridItem(
                        label = "最低心率",
                        value = stats.min?.toString() ?: "--",
                        unit = "BPM",
                        valueColor = MindBodyColors.OnBackgroundSecondary
                    )
                ),
                columns = 2
            )
        }

        // ── 折叠图表（点击展开）────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showChart = !showChart }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史趋势图",
                style = CardTitle.copy(color = MindBodyColors.OnBackgroundSecondary)
            )
            Text(
                text = if (showChart) "收起 ↑" else "展开 ↓",
                style = StatLabel.copy(
                    color = MindBodyColors.CalmTeal,
                    fontSize = 12.sp
                )
            )
        }

        AnimatedVisibility(
            visible = showChart,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            PremiumCard(cornerRadius = 32.dp) {
                MindBodySplineChart(
                    state = chartState,
                    onPresetSelected = viewModel::setChartPreset,
                    onWindowPan = viewModel::panChartWindow
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SkinTemperatureNarrativeCard(
    tempCelsius: Float?,
    connectionState: ConnectionState
) {
    val narrativeText = when {
        connectionState != ConnectionState.CONNECTED ->
            "Polar Loop 当前未连接，无法获取实时皮肤温度数据。请确认手环佩戴并开启蓝牙。"
        tempCelsius == null ->
            "正在等待温度传感器数据，请保持手环贴合手腕。"
        tempCelsius < 33f ->
            "当前皮肤温度 ${"%.1f".format(tempCelsius)}°C，偏低。可能处于安静或环境温度较低的状态。"
        tempCelsius > 36.5f ->
            "当前皮肤温度 ${"%.1f".format(tempCelsius)}°C，略偏高。可能正在运动或处于较温暖的环境中。"
        else ->
            "当前皮肤温度 ${"%.1f".format(tempCelsius)}°C，处于正常舒适区间（33–36.5°C）。"
    }

    NarrativeCard(
        accentColor = MindBodyColors.Amber,
        badgeLabel = if (tempCelsius != null) "${"%.1f".format(tempCelsius)}°C · 皮肤温度" else "皮肤温度"
    ) {
        NarrativeBody(text = narrativeText)
        if (connectionState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionStatusCapsule(
                statusText = "Loop 手腕表面温度 · 实时",
                isActive = true
            )
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "连接就绪 · 正在采集"
    ConnectionState.CONNECTING -> "连接中…"
    ConnectionState.BLE_OFF -> "请打开手机蓝牙"
    ConnectionState.DISCONNECTED -> "未连接设备"
}
