package com.mehul.inkless.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app uses, in one place. The values come from the Figma design: a warm
 * off-white page, a deep green accent and green-tinted cards, with a matching dark set.
 */
@Immutable
data class InklessColors(
    /** The page behind everything. */
    val background: Color,
    /** Raised cards, inputs, sheets and the bottom bar. */
    val surface: Color,
    /** Green-tinted cards and icon tiles, the selected pill. */
    val tint: Color,
    /** Tracks behind rings and bars, and past months in charts. */
    val tintStrong: Color,
    /** Buttons, links and accents. */
    val primary: Color,
    /** Text and icons on [primary]. */
    val onPrimary: Color,
    /** Main text. */
    val ink: Color,
    /** Secondary text: dates, details, captions. */
    val muted: Color,
    /** Hairline borders and dividers. */
    val hairline: Color,
    /** Beige cards and chips for things that need a look: "Check", "Blocked", verify email. */
    val warm: Color,
    /** Text on [warm]. */
    val onWarm: Color,
    /** Destructive actions and errors. */
    val danger: Color,
    /** Light red behind [danger] text. */
    val dangerTint: Color,
) {
    /** Money coming in. */
    val credit: Color get() = primary
}

internal val LightInklessColors = InklessColors(
    background = Color(0xFFF7F6F0),
    surface = Color(0xFFFFFEFA),
    tint = Color(0xFFE3EDE4),
    tintStrong = Color(0xFFC5D7C9),
    primary = Color(0xFF236B53),
    onPrimary = Color(0xFFFFFEFA),
    ink = Color(0xFF202B27),
    muted = Color(0xFF63706A),
    hairline = Color(0xFFDEDFD6),
    warm = Color(0xFFF3EBDA),
    onWarm = Color(0xFF7A5A30),
    danger = Color(0xFFA23D3D),
    dangerTint = Color(0xFFF8E3E0),
)

internal val DarkInklessColors = InklessColors(
    background = Color(0xFF141E1A),
    surface = Color(0xFF1F2C25),
    tint = Color(0xFF2A4235),
    tintStrong = Color(0xFF35453B),
    primary = Color(0xFF9CD7B5),
    onPrimary = Color(0xFF141E1A),
    ink = Color(0xFFEDF0E7),
    muted = Color(0xFFA7B5AA),
    hairline = Color(0xFF35453B),
    warm = Color(0xFF3A3226),
    onWarm = Color(0xFFE5C99C),
    danger = Color(0xFFF0A49B),
    dangerTint = Color(0xFF4A2724),
)
