package com.timedrecorder.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角形状（Material Design 3）。
 *
 * 对齐 HTML 原型：sm=8 / md=12 / lg=16 / xl=28。
 * M3 用较大圆角营造柔和、现代的观感。
 */
val RecorderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
