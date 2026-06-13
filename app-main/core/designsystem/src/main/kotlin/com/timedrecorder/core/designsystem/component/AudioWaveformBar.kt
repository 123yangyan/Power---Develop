package com.timedrecorder.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 录音页声纹占位动画：用跳动的条形模拟「系统正在听」的反馈感。
 * 第一期无真实振幅数据，采用伪动画；V2 可接入 AudioRecord 振幅。
 *
 * @param isActive 是否正在录音（暂停时动画减弱）
 */
@Composable
fun AudioWaveformBar(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 12,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)       // 固定高度：防止条形动画变化引起容器尺寸跳动
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { index ->
            val anim by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + index * 50,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar_$index",
            )
            val heightFactor = if (isActive) anim else 0.2f
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + 24 * heightFactor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.error.copy(
                            alpha = if (isActive) 0.6f + 0.4f * heightFactor else 0.3f,
                        ),
                    ),
            )
        }
    }
}
