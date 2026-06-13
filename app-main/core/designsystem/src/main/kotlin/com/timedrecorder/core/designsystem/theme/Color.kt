package com.timedrecorder.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用配色（Material Design 3）。
 *
 * 以品牌种子色「淡紫灰 #7B6FA6」生成的 M3 协调调色板，
 * 与 [定时录音助手-界面原型] 保持一致。
 * 浅色用 Light 前缀，深色用 Dark 前缀。
 */

// ---------------- 浅色模式（淡紫灰 · 低饱和无压力） ----------------
val LightPrimary = Color(0xFF7B6FA6)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8E4F3)
val LightOnPrimaryContainer = Color(0xFF2A2540)

val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1E192B)

val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD8E4)
val LightOnTertiaryContainer = Color(0xFF31111D)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF5F4F8)
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)

// ---------------- 深色模式（紫灰系，与浅色品牌色统一） ----------------
val DarkPrimary = Color(0xFFCBBEFF)
val DarkOnPrimary = Color(0xFF33275A)
val DarkPrimaryContainer = Color(0xFF4A4268)
val DarkOnPrimaryContainer = Color(0xFFE8E4F3)

val DarkSecondary = Color(0xFFCBC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)

val DarkTertiary = Color(0xFFEFB8C8)
val DarkOnTertiary = Color(0xFF492532)
val DarkTertiaryContainer = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF1C1B1F)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkSurface = Color(0xFF1C1B1F)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF49454F)

/**
 * 业务「状态色」— M3 语义容器，用于上传/处理状态徽章。
 */
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
)

val LightStatusColors = StatusColors(
    success = Color(0xFF2E6B34),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB6F1AF),
    warning = Color(0xFF845400),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB3),
    info = Color(0xFF1C5C8C),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFCDE5FF),
)

val DarkStatusColors = StatusColors(
    success = Color(0xFF9BD494),
    onSuccess = Color(0xFF00390A),
    successContainer = Color(0xFF14521C),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF462A00),
    warningContainer = Color(0xFF643F00),
    info = Color(0xFF96CCFF),
    onInfo = Color(0xFF003355),
    infoContainer = Color(0xFF004A77),
)
