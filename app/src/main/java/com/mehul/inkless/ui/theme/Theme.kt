package com.mehul.inkless.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalInklessColors = staticCompositionLocalOf { LightInklessColors }
val LocalInklessType = staticCompositionLocalOf { InklessTypeScale }

/** Shortcuts for the app's own tokens: `Inkless.colors.tint`, `Inkless.type.moneyHero`. */
object Inkless {
    val colors: InklessColors
        @Composable @ReadOnlyComposable get() = LocalInklessColors.current

    val type: InklessType
        @Composable @ReadOnlyComposable get() = LocalInklessType.current
}

/**
 * The Figma colours on every phone: the palette doesn't follow the wallpaper, so the app looks
 * the same in every screenshot and on every friend's phone.
 */
@Composable
fun InklessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkInklessColors else LightInklessColors
    CompositionLocalProvider(
        LocalInklessColors provides colors,
        LocalInklessType provides InklessTypeScale,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(darkTheme),
            typography = InklessTypography,
            shapes = InklessShapes,
            content = content,
        )
    }
}

// Material components (buttons, sheets, dialogs, switches) read these slots, so they follow the
// Figma palette without being styled one by one.
private fun InklessColors.toMaterial(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = tint,
        onPrimaryContainer = ink,
        inversePrimary = if (dark) LightInklessColors.primary else DarkInklessColors.primary,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = tint,
        onSecondaryContainer = primary,
        tertiary = onWarm,
        onTertiary = warm,
        tertiaryContainer = warm,
        onTertiaryContainer = onWarm,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = tint,
        onSurfaceVariant = muted,
        surfaceTint = background,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = tint,
        inverseSurface = ink,
        inverseOnSurface = background,
        outline = hairline,
        outlineVariant = hairline,
        error = danger,
        onError = if (dark) background else surface,
        errorContainer = dangerTint,
        onErrorContainer = danger,
    )
}
