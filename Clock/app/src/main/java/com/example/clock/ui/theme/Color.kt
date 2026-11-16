package com.example.clock.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Warm graphite / ink surfaces with an amber accent. An elegant clock feel,
 * intentional dark mode, no dynamic colour dependence.
 */
object ClockColor {
    // Core palette
    val Ink = Color(0xFF1C1917)          // near-black warm graphite
    val InkSoft = Color(0xFF4A4036)      // muted graphite text
    val Paper = Color(0xFFFAF7F2)        // warm off-white surface
    val PaperVariant = Color(0xFFF2E7D6) // tonal card surface
    val Amber = Color(0xFFC48A2C)        // primary accent
    val AmberLight = Color(0xFFF7EBDC)   // primary container (light)
    val AmberDark = Color(0xFFE5A84D)    // primary accent (dark theme)
    val SurfaceDark = Color(0xFF1A1816)  // warm graphite surface
    val BackgroundDark = Color(0xFF151311)
    val SurfaceVariantDark = Color(0xFF26221F)
    val OnSurfaceDark = Color(0xFFF3EBDD)
    val Error = Color(0xFFB4441E)        // warm rust
    val ErrorDark = Color(0xFFE87454)

    val Light = ColorScheme(
        primary = Amber,
        onPrimary = Color.White,
        primaryContainer = AmberLight,
        onPrimaryContainer = Color(0xFF4A3308),
        secondary = Color(0xFF8A7358),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9DBC9),
        onSecondaryContainer = Color(0xFF3A2E23),
        tertiary = Color(0xFF9A6B3E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEBD9C4),
        onTertiaryContainer = Color(0xFF4A300A),
        error = Error,
        onError = Color.White,
        errorContainer = Color(0xFFF9E3DB),
        onErrorContainer = Color(0xFF6B2412),
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = PaperVariant,
        onSurfaceVariant = Color(0xFF7A6856),
        inverseSurface = Ink,
        inverseOnSurface = Paper,
        inversePrimary = AmberDark,
        outline = Color(0xFFD8CDB9),
        outlineVariant = Color(0xFFE7DDCA),
        surfaceTint = Amber,
        scrim = Color(0xFF000000),
    )

    val Dark = ColorScheme(
        primary = AmberDark,
        onPrimary = Color(0xFF1C1917),
        primaryContainer = Color(0xFF6B4A0E),
        onPrimaryContainer = Color(0xFFF7EBDC),
        secondary = Color(0xFFB8A27E),
        onSecondary = Color(0xFF1C1917),
        secondaryContainer = Color(0xFF5E4D33),
        onSecondaryContainer = Color(0xFFF3EBDD),
        tertiary = Color(0xFFC48A2C),
        onTertiary = Color(0xFF1C1917),
        tertiaryContainer = Color(0xFF8A6226),
        onTertiaryContainer = Color(0xFFF7EBDC),
        error = ErrorDark,
        onError = Color(0xFF1C1917),
        errorContainer = Color(0xFF6B2E1E),
        onErrorContainer = Color(0xFFF9E3DB),
        background = BackgroundDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = Color(0xFFAD9D8B),
        inverseSurface = OnSurfaceDark,
        inverseOnSurface = SurfaceDark,
        inversePrimary = Amber,
        outline = Color(0xFF3F372E),
        outlineVariant = Color(0xFF2A241E),
        surfaceTint = AmberDark,
        scrim = Color(0xFF000000),
    )
}
