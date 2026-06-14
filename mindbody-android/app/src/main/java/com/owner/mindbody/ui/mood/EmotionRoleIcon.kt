package com.owner.mindbody.ui.mood

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 情绪角色图标。
 * 使用 [ContentScale.Fit] + 内边距，避免圆形裁剪切掉角色头部/四肢。
 */
@Composable
fun EmotionRoleIcon(
    role: EmotionRole,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    selected: Boolean = false,
    idleAnimation: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val scale = if (idleAnimation && onClick != null) {
        val transition = rememberInfiniteTransition(label = "role_idle")
        val animated by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "role_scale"
        )
        animated
    } else {
        1f
    }

    val borderColor = if (selected) {
        Color(role.accentColorHex).copy(alpha = 0.8f)
    } else {
        MindBodyColors.CardBorder
    }

    // 图标与圆形边框之间的留白，防止 Fit 后仍贴边被 clip
    val innerPadding = size * 0.1f

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(role.accentColorHex).copy(alpha = 0.12f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (role.iconRes != null) {
            Image(
                painter = painterResource(role.iconRes),
                contentDescription = role.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentScale = ContentScale.Fit
            )
        }
    }
}
