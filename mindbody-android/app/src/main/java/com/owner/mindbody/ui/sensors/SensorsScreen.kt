package com.owner.mindbody.ui.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.polar.AccSample
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.components.StatGrid
import com.owner.mindbody.ui.components.StatItem
import com.owner.mindbody.ui.components.StreamStatusBadge
import com.owner.mindbody.ui.theme.BpmHero
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.polar.sdk.api.model.PolarPpiData
import kotlin.math.sqrt

@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val currentAcc by viewModel.currentAcc.collectAsState()
    val latestPpi by viewModel.latestPpi.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        AccelerometerCard(acc = currentAcc)

        PpiCard(ppi = latestPpi)
    }
}

/** 三轴加速度实时卡片。 */
@Composable
private fun AccelerometerCard(acc: AccSample?) {
    val magnitudeG = acc?.let { sample ->
        sqrt(
            (sample.x * sample.x + sample.y * sample.y + sample.z * sample.z).toDouble()
        ) / 1000.0
    }

    PremiumCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "加速度 ACC", style = CardTitle)
                Text(
                    text = "三轴运动 / 姿态",
                    style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(MindBodyShapes.Badge)
                    .background(MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "50Hz",
                    style = StatLabel.copy(color = MindBodyColors.PrimaryIndigo)
                )
            }
        }
        StatGrid(
            items = listOf(
                StatItem("X", acc?.x?.toString() ?: "--", MindBodyColors.PrimaryIndigo),
                StatItem("Y", acc?.y?.toString() ?: "--", MindBodyColors.PrimaryIndigo),
                StatItem("Z", acc?.z?.toString() ?: "--", MindBodyColors.PrimaryIndigo),
                StatItem(
                    "|a|",
                    magnitudeG?.let { "%.2f g".format(it) } ?: "--",
                    MindBodyColors.Emerald
                )
            ),
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = "单位：millig（X/Y/Z）；合加速度已换算为 g",
            style = StatLabel.copy(
                fontSize = 11.sp,
                color = MindBodyColors.OnBackgroundSecondary
            ),
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** PPI 心跳间期实时卡片。 */
@Composable
private fun PpiCard(ppi: PolarPpiData.PolarPpiSample?) {
    val blocked = ppi?.blockerBit == true
    val valueColor = if (blocked) {
        MindBodyColors.OnBackgroundSecondary
    } else {
        MindBodyColors.HeartRed
    }

    PremiumCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "PPI 心跳间期", style = CardTitle)
                Text(
                    text = "相邻两次心跳的时间间隔",
                    style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(MindBodyShapes.Badge)
                    .background(MindBodyColors.HeartRed.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "实时",
                    style = StatLabel.copy(color = MindBodyColors.HeartRed)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = ppi?.ppi?.toString() ?: "--",
                style = BpmHero.copy(
                    fontSize = 48.sp,
                    color = valueColor
                )
            )
            Text(
                text = "ms",
                style = StatLabel.copy(
                    fontSize = 14.sp,
                    color = valueColor
                ),
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
            )
        }

        if (blocked) {
            Text(
                text = "运动干扰：当前 PPI 可能不准确",
                style = StatLabel.copy(
                    fontSize = 12.sp,
                    color = MindBodyColors.Amber
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        StatGrid(
            items = listOf(
                StatItem("心率", ppi?.hr?.toString() ?: "--", MindBodyColors.HeartRed),
                StatItem("误差", ppi?.errorEstimate?.let { "$it ms" } ?: "--"),
                StatItem(
                    "皮肤接触",
                    when {
                        ppi == null -> "--"
                        !ppi.skinContactSupported -> "N/A"
                        ppi.skinContactStatus -> "良好"
                        else -> "较差"
                    },
                    if (ppi?.skinContactStatus == true) MindBodyColors.Emerald
                    else MindBodyColors.OnBackgroundSecondary
                )
            ),
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
