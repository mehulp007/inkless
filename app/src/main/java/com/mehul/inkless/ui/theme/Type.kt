package com.mehul.inkless.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mehul.inkless.R

// Both fonts are variable fonts: one file holds every weight, and the weight is picked here.
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Lora: the serif for page headlines ("The ledger that writes itself."). */
val Lora = FontFamily(
    variable(R.font.lora, FontWeight.Normal),
    variable(R.font.lora, FontWeight.Medium),
)

/** Inter: everything else, including money. */
val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
    variable(R.font.inter, FontWeight.Bold),
)

// Digits all the same width, so amounts line up in lists and don't jiggle when they change.
private const val TABULAR = "tnum"

internal val InklessTypography = Typography(
    // Lora headlines, biggest first: Welcome, page headlines, sheet headlines.
    displayLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Normal, fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em),
    displaySmall = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp, fontFeatureSettings = TABULAR),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
)

/** Text styles Material doesn't have a slot for: money at its different sizes. */
@Immutable
data class InklessType(
    /** Safe to spend on Home: "₹84". */
    val moneyHero: TextStyle,
    /** Totals at the top of a page: "₹780", "₹9,300". */
    val moneyLarge: TextStyle,
    /** Amounts inside cards: "₹420". */
    val moneyMedium: TextStyle,
    /** Amounts at the end of a list row. */
    val moneyRow: TextStyle,
    /** Small caps-style labels: "LESS TYPING. MORE LIVING.", "1 OF 3". */
    val overline: TextStyle,
)

internal val InklessTypeScale = InklessType(
    moneyHero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    moneyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    moneyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TABULAR),
    moneyRow = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR),
    overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
)
