package com.owner.mindbody.ui.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.polar.AccSample
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.ui.components.HeroIndicator
import com.owner.mindbody.ui.components.MicroGrid
import com.owner.mindbody.ui.components.MicroGridItem
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.components.StreamStatusBadge
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.polar.sdk.api.model.PolarPpiData
import kotlin.math.sqrt

/**
 * 传感器页 — 重构为"数据密集但视觉克制"布局：
 * - Head  (20%): HeroIndicator（合加速度幅值 / 运动状态结论）
 * - Middle (50%): NarrativeCard（当前运动状态说明 + PPI 质量评语）
 * - Bottom (30%): MicroGrid（ACC X/Y/Z/|a| + PPI/HR/误差/皮肤接触）
 */
@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val currentAcc by viewModel.currentAcc.collectAsState()
    val latestPpi by viewModel.latestPpi.collectAsState()

    val magnitudeG = currentAcc?.let { s ->
        sqrt((s.x * s.x + s.y * s.y + s.z * s.z).toDouble()) / 1000.0
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
            eyebrow = "POLAR LOOP",
            title = "传感器数据",
            trailing = {
                StreamStatusBadge(connected = connectionState == ConnectionState.CONNECTED)
            }
        )

        // ── HEAD (20%): 合加速度 Hero ─────────────────────────────────────
        val motionLabel = classifyMotion(magnitudeG)
        val heroProgress = magnitudeG?.let { ((it - 0.9) / 1.1).coerceIn(0.0, 1.0).toFloat() } ?: 0f

        HeroIndicator(
            primaryLabel = magnitudeG?.let { "%.2f".format(it) } ?: "--",
            secondaryLabel = "$motionLabel · 合加速度",
            accentColor = MindBodyColors.PrimaryIndigo,
            surfaceColor = MindBodyColors.PrimaryIndigoSurface,
            trackProgress = heroProgress,
            trackUnitLabel = "g",
            height = 200.dp
        )

        // ── MIDDLE (50%): 运动状态 + PPI 质量叙事卡 ────────────────────────
        SensorNarrativeCard(acc = currentAcc, ppi = latestPpi, magnitudeG = magnitudeG)

        // ── BOTTOM (30%): 全指标 MicroGrid ────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "原始传感器读数",
                style = CardTitle.copy(color = MindBodyColors.OnBackgroundSecondary)
            )
            MicroGrid(
                items = listOf(
                    MicroGridItem(
                        label = "ACC X",
                        value = currentAcc?.x?.toString() ?: "--",
                        unit = "mg",
                        valueColor = MindBodyColors.PrimaryIndigo
                    ),
                    MicroGridItem(
                        label = "ACC Y",
                        value = currentAcc?.y?.toString() ?: "--",
                        unit = "mg",
                        valueColor = MindBodyColors.PrimaryIndigo
                    ),
                    MicroGridItem(
                        label = "ACC Z",
                        value = currentAcc?.z?.toString() ?: "--",
                        unit = "mg",
                        valueColor = MindBodyColors.PrimaryIndigo
                    ),
                    MicroGridItem(
                        label = "|a| 合加速度",
                        value = magnitudeG?.let { "%.2f".format(it) } ?: "--",
                        unit = "g",
                        valueColor = MindBodyColors.Emerald,
                        sparklineColor = MindBodyColors.Emerald
                    ),
                    MicroGridItem(
                        label = "PPI 心跳间期",
                        value = latestPpi?.ppi?.toString() ?: "--",
                        unit = "ms",
                        valueColor = if (latestPpi?.blockerBit == true)
                            MindBodyColors.OnBackgroundSecondary else MindBodyColors.HeartRed
                    ),
                    MicroGridItem(
                        label = "实时心率",
                        value = latestPpi?.hr?.toString() ?: "--",
                        unit = "BPM",
                        valueColor = MindBodyColors.HeartRed
                    ),
                    MicroGridItem(
                        label = "PPI 误差",
                        value = latestPpi?.errorEstimate?.let { "$it ms" } ?: "--",
                        valueColor = MindBodyColors.Amber
                    ),
                    MicroGridItem(
                        label = "皮肤接触",
                        value = latestPpi.let { ppi ->
                            when {
                                ppi == null -> "--"
                                !ppi.skinContactSupported -> "N/A"
                                ppi.skinContactStatus -> "良好"
                                else -> "较差"
                            }
                        },
                        valueColor = if (latestPpi?.skinContactStatus == true)
                            MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary
                    )
                ),
                columns = 2
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SensorNarrativeCard(
    acc: AccSample?,
    ppi: PolarPpiData.PolarPpiSample?,
    magnitudeG: Double?
) {
    val motionNarrative = when {
        acc == null ->
            "暂未收到加速度数据，请确认 Polar Loop 佩戴正常并蓝牙连接。"
        magnitudeG != null && magnitudeG < 0.95 ->
            "当前合加速度 ${"%.2f".format(magnitudeG)}g，接近静止状态。适合采集高质量的心率变异性数据。"
        magnitudeG != null && magnitudeG < 1.5 ->
            "当前合加速度 ${"%.2f".format(magnitudeG)}g，检测到轻微运动。PPI 数据质量可能受到影响，建议保持相对静止以获取准确 HRV。"
        magnitudeG != null ->
            "当前合加速度 ${"%.2f".format(magnitudeG)}g，处于活跃运动状态。PPI 采集可能有运动干扰（blockerBit 激活）。"
        else -> "正在分析运动状态…"
    }

    val ppiQualityNote = when {
        ppi == null -> ""
        ppi.blockerBit -> "\n\n⚠ 检测到运动干扰：当前 PPI 可能不够准确（blockerBit=true）。静止后 HRV 计算精度将恢复。"
        ppi.skinContactSupported && !ppi.skinContactStatus ->
            "\n\n皮肤接触质量较差，请调整手环位置，使其贴合手腕内侧皮肤。"
        else -> ""
    }

    NarrativeCard(
        accentColor = MindBodyColors.PrimaryIndigo,
        badgeLabel = "运动 · 心跳质量"
    ) {
        NarrativeBody(text = motionNarrative + ppiQualityNote)
    }
}

private fun classifyMotion(magnitudeG: Double?): String = when {
    magnitudeG == null -> "等待数据"
    magnitudeG < 0.95 -> "静止"
    magnitudeG < 1.2 -> "轻微活动"
    magnitudeG < 2.0 -> "中等运动"
    else -> "剧烈运动"
}

