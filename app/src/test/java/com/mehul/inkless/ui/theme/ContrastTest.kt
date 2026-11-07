package com.mehul.inkless.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Every text colour against every surface it's drawn on, in light and dark, against WCAG AA:
 * 4.5:1 for body text. Lines and the empty part of bars only need 3:1 against what's around them.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun InklessColors.textPairs() = mapOf(
        "ink on background" to (ink to background),
        "ink on surface" to (ink to surface),
        "ink on tint" to (ink to tint),
        "muted on background" to (muted to background),
        "muted on surface" to (muted to surface),
        "muted on tint" to (muted to tint),
        "primary on background" to (primary to background),
        "primary on surface" to (primary to surface),
        "primary on tint" to (primary to tint),
        "on-primary on primary" to (onPrimary to primary),
        "on-warm on warm" to (onWarm to warm),
        "danger on background" to (danger to background),
        "danger on surface" to (danger to surface),
        "danger on danger tint" to (danger to dangerTint),
    )

    private fun check(name: String, colors: InklessColors) {
        colors.textPairs().forEach { (pair, colours) ->
            val (fg, bg) = colours
            assertWithMessage("$name: $pair").that(ratio(fg, bg)).isAtLeast(4.5)
        }
        // A filled bar has to stand out from its track.
        assertWithMessage("$name: primary on its track").that(ratio(colors.primary, colors.tintStrong)).isAtLeast(3.0)
    }

    @Test
    fun `light palette text is readable`() = check("light", LightInklessColors)

    @Test
    fun `dark palette text is readable`() = check("dark", DarkInklessColors)
}
