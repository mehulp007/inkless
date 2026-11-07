package com.mehul.inkless.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mehul.inkless.ui.theme.Inkless

/** A rounded bar filling up to [fraction]: budgets, imports, places you spend at. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = Inkless.colors.primary,
    track: Color = Inkless.colors.tintStrong,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(color, size = Size(maxOf(size.width * clamped, size.height), size.height), cornerRadius = radius)
        }
    }
}

/** How much of a budget is spent; the bar turns red once it's over. */
@Composable
fun BudgetBar(spentPaise: Long, budgetPaise: Long, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val fraction = if (budgetPaise > 0) spentPaise.toFloat() / budgetPaise else 0f
    ProgressBar(
        fraction = fraction,
        modifier = modifier,
        height = height,
        color = if (fraction > 1f) Inkless.colors.danger else Inkless.colors.primary,
    )
}
