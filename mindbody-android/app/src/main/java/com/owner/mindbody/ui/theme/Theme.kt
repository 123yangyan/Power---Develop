package com.owner.mindbody.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MindBodyLightColors = lightColorScheme(
    primary = MindBodyColors.PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = MindBodyColors.PrimaryIndigoLight,
    onPrimaryContainer = MindBodyColors.PrimaryIndigo,
    secondary = MindBodyColors.HeartRed,
    onSecondary = Color.White,
    background = MindBodyColors.Background,
    onBackground = MindBodyColors.OnBackground,
    surface = MindBodyColors.CardSurfaceSolid,
    onSurface = MindBodyColors.OnBackground,
    surfaceVariant = MindBodyColors.StatCellBg,
    onSurfaceVariant = MindBodyColors.OnBackgroundSecondary,
    outline = MindBodyColors.CardBorder,
    error = Color(0xFFDC2626)
)

@Composable
fun MindBodyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MindBodyLightColors,
        typography = MindBodyTypography,
        content = content
    )
}
