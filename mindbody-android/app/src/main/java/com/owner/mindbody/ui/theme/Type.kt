package com.owner.mindbody.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MindBodyTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
        color = MindBodyColors.OnBackground
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        color = MindBodyColors.OnBackground.copy(alpha = 0.7f)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = MindBodyColors.OnBackgroundSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        color = MindBodyColors.OnBackgroundSecondary
    )
)

val SectionEyebrow = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 10.sp,
    letterSpacing = 2.sp,
    color = MindBodyColors.OnBackgroundMuted
)

val PageTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 24.sp,
    letterSpacing = (-0.5).sp,
    color = MindBodyColors.OnBackground
)

val CardTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 12.sp,
    letterSpacing = 1.2.sp,
    color = MindBodyColors.OnBackground.copy(alpha = 0.7f)
)

val BpmHero = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 86.sp,
    letterSpacing = (-2).sp,
    color = MindBodyColors.HeartRed
)

val StatValue = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 15.sp,
    color = MindBodyColors.OnBackground
)

val StatLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 9.sp,
    color = MindBodyColors.OnBackgroundSecondary
)

val NavLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 9.sp,
    letterSpacing = 1.sp
)
