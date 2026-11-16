package com.example.clock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Characterful type pairing:
 * - system sans-serif for display / body UI (elegant clock feel)
 * - system monospace for stopwatch / timer digits (tabular numerals)
 */
val ClockDisplayFont = FontFamily.SansSerif
val ClockBodyFont = FontFamily.SansSerif
val ClockMonoFont = FontFamily.Monospace

val ClockTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ClockDisplayFont,
        fontSize = 48.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = ClockDisplayFont,
        fontSize = 40.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = ClockDisplayFont,
        fontSize = 28.sp,
        fontWeight = FontWeight.W700,
    ),
    titleLarge = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 20.sp,
        fontWeight = FontWeight.W600,
    ),
    titleMedium = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.W500,
    ),
    bodyLarge = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.W500,
    ),
    bodyMedium = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
    ),
    bodySmall = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
    ),
    labelLarge = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.1.em,
    ),
    labelMedium = TextStyle(
        fontFamily = ClockBodyFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.1.em,
    ),
)

/** Large clock numerals (ring screen, timer, stopwatch). */
val ClockTimeStyle: TextStyle = TextStyle(
    fontFamily = ClockDisplayFont,
    fontSize = 64.sp,
    fontWeight = FontWeight.W800,
    letterSpacing = (-0.03).em,
)

/** Tabular monospace digits for stopwatch / timer counters. */
val ClockCounterStyle: TextStyle = TextStyle(
    fontFamily = ClockMonoFont,
    fontSize = 64.sp,
    fontWeight = FontWeight.W700,
    fontFeatureSettings = "tnum",
)
