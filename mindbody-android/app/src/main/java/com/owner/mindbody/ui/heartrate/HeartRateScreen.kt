package com.owner.mindbody.ui.heartrate

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.ui.components.ConnectionStatusCapsule
import com.owner.mindbody.ui.components.MindBodySplineChart
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

@Composable
fun HeartRateScreen(
    viewModel: HeartRateViewModel = viewModel()
) {
    val currentHr by viewModel.currentHr.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val samples by viewModel.todaySamples.collectAsState()
    val stats by viewModel.todayStats.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startBackgroundStream()
        onDispose { viewModel.stopBackgroundStream() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        HeartRateHeroCard(
            bpm = currentHr,
            connectionState = connectionState
        )

        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "今日生理负荷", style = CardTitle)
                Box(
                    modifier = Modifier
                        .clip(MindBodyShapes.Badge)
                        .background(MindBodyColors.Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "24h 缓存",
                        style = StatLabel.copy(color = MindBodyColors.Amber)
                    )
                }
            }
            StatGrid(
                items = listOf(
                    StatItem("样本数", stats.count.toString()),
                    StatItem("平均值", stats.average?.toString() ?: "--", MindBodyColors.PrimaryIndigo),
                    StatItem("最高值", stats.max?.toString() ?: "--", MindBodyColors.HeartRed),
                    StatItem("最低值", stats.min?.toString() ?: "--", MindBodyColors.OnBackgroundSecondary)
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        PremiumCard(cornerRadius = 32.dp) {
            MindBodySplineChart(samples = samples)
        }
    }
}

@Composable
private fun HeartRateHeroCard(
    bpm: Int?,
    connectionState: ConnectionState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bpmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val waveRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "waveRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.HeroCard)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MindBodyColors.HeroGradientStart,
                        MindBodyColors.HeroGradientMid,
                        MindBodyColors.HeroGradientEnd
                    )
                )
            )
            .border(1.dp, MindBodyColors.CardBorder, MindBodyShapes.HeroCard)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .offset(x = (-20).dp, y = (-10).dp)
                .scale(1.2f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MindBodyColors.HeartRed.copy(alpha = 0.12f),
                            MindBodyColors.HeartRed.copy(alpha = 0f)
                        )
                    ),
                    shape = MindBodyShapes.HeroCard
                )
        )
        Box(
            modifier = Modifier
                .size(128.dp)
                .offset(x = 30.dp, y = 20.dp)
                .scale(1f + waveRotation / 3600f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MindBodyColors.PrimaryIndigo.copy(alpha = 0.1f),
                            MindBodyColors.PrimaryIndigo.copy(alpha = 0f)
                        )
                    ),
                    shape = MindBodyShapes.HeroCard
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.scale(pulseScale)
            ) {
                Text(
                    text = bpm?.toString() ?: "--",
                    style = BpmHero
                )
                Text(
                    text = "BPM",
                    style = StatLabel.copy(
                        fontSize = 14.sp,
                        color = MindBodyColors.PrimaryIndigo
                    ),
                    modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)
                )
            }
            ConnectionStatusCapsule(
                statusText = connectionLabel(connectionState),
                isActive = connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "连接就绪 • 正在采集"
    ConnectionState.CONNECTING -> "连接中…"
    ConnectionState.BLE_OFF -> "请打开手机蓝牙"
    ConnectionState.DISCONNECTED -> "未连接设备"
}
