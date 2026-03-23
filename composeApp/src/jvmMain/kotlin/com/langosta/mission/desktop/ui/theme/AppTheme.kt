package com.langosta.mission.desktop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Tailwind Colors ────────────────────────────────────────────
object TailwindColors {
    // Zinc (backgrounds)
    val Zinc950 = Color(0xFF09090B)
    val Zinc900 = Color(0xFF18181B)
    val Zinc800 = Color(0xFF27272A)
    val Zinc700 = Color(0xFF3F3F46)
    val Zinc400 = Color(0xFFA1A1AA)
    val Zinc200 = Color(0xFFE4E4E7)
    val Zinc50  = Color(0xFFFAFAFA)

    // Emerald (primary accent)
    val Emerald400 = Color(0xFF34D399)
    val Emerald500 = Color(0xFF10B981)
    val Emerald900 = Color(0xFF064E3B)

    // Sky (secondary/info)
    val Sky400 = Color(0xFF38BDF8)
    val Sky500 = Color(0xFF0EA5E9)
    val Sky900 = Color(0xFF0C4A6E)

    // Rose (error)
    val Rose400 = Color(0xFFFB7185)
    val Rose500 = Color(0xFFF43F5E)
    val Rose900 = Color(0xFF881337)

    // Amber (warning)
    val Amber400 = Color(0xFFFBBF24)
    val Amber500 = Color(0xFFF59E0B)

    // Violet (tertiary)
    val Violet400 = Color(0xFFA78BFA)
    val Violet500 = Color(0xFF8B5CF6)
}

// ── Dark Color Scheme ──────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    background         = TailwindColors.Zinc950,
    surface            = TailwindColors.Zinc900,
    surfaceVariant     = TailwindColors.Zinc800,
    outline            = TailwindColors.Zinc700,
    primary            = TailwindColors.Emerald400,
    onPrimary          = TailwindColors.Zinc950,
    primaryContainer   = TailwindColors.Emerald900,
    onPrimaryContainer = TailwindColors.Emerald400,
    secondary          = TailwindColors.Sky400,
    onSecondary        = TailwindColors.Zinc950,
    secondaryContainer = TailwindColors.Sky900,
    onSecondaryContainer = TailwindColors.Sky400,
    tertiary           = TailwindColors.Violet400,
    onTertiary         = TailwindColors.Zinc950,
    tertiaryContainer  = TailwindColors.Violet500,
    error              = TailwindColors.Rose400,
    onError            = TailwindColors.Zinc950,
    errorContainer     = TailwindColors.Rose900,
    onErrorContainer   = TailwindColors.Rose400,
    onBackground       = TailwindColors.Zinc200,
    onSurface          = TailwindColors.Zinc400,
    onSurfaceVariant   = TailwindColors.Zinc400,
)

// ── Light Color Scheme ─────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    background         = TailwindColors.Zinc50,
    surface            = TailwindColors.Zinc200,
    surfaceVariant     = TailwindColors.Zinc200,
    outline            = TailwindColors.Zinc400,
    primary            = TailwindColors.Emerald500,
    onPrimary          = TailwindColors.Zinc50,
    primaryContainer   = TailwindColors.Emerald900,
    onPrimaryContainer = TailwindColors.Emerald400,
    secondary          = TailwindColors.Sky500,
    onSecondary        = TailwindColors.Zinc50,
    secondaryContainer = TailwindColors.Sky900,
    onSecondaryContainer = TailwindColors.Sky400,
    tertiary           = TailwindColors.Violet500,
    onTertiary         = TailwindColors.Zinc50,
    tertiaryContainer  = TailwindColors.Violet400,
    error              = TailwindColors.Rose500,
    onError            = TailwindColors.Zinc50,
    errorContainer     = TailwindColors.Rose900,
    onErrorContainer   = TailwindColors.Rose400,
    onBackground       = TailwindColors.Zinc900,
    onSurface          = TailwindColors.Zinc700,
    onSurfaceVariant   = TailwindColors.Zinc700,
)

// ── Theme Entry Point ──────────────────────────────────────────
@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
