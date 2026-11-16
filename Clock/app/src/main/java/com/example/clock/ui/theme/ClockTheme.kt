package com.example.clock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Application theme. Provides a warm graphite / ink Material3 colour scheme
 * with an amber accent, paired with the condensed display and monospace
 * typography. No dynamic colour is used — light and dark are both intentional.
 */
@Composable
fun ClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ClockColor.Dark else ClockColor.Light
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClockTypography,
        content = content,
    )
}
