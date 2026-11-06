package com.mehul.inkless.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A ring filling up as the budget is spent; it turns to the error colour past 100%. */
@Composable
fun SpendRing(fraction: Float, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (fraction > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(fill, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = stroke)
        }
        content()
    }
}

/**
 * Spending so far this cycle (solid) against an even pace that ends exactly at the budget on the
 * day before the next allowance (dashed). Above the dashed line means spending faster than the money lasts.
 */
@Composable
fun PaceChart(cumulativePaise: List<Long>, budgetPaise: Long, cycleDays: Int, modifier: Modifier = Modifier) {
    val spentColor = MaterialTheme.colorScheme.primary
    val paceColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        if (cycleDays <= 0 || budgetPaise <= 0) return@Canvas
        val top = maxOf(budgetPaise, cumulativePaise.maxOrNull() ?: 0L).toFloat()
        fun x(day: Int) = size.width * day / cycleDays
        fun y(paise: Long) = size.height - size.height * (paise.coerceAtLeast(0) / top)

        drawLine(
            paceColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, y(budgetPaise)),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
        if (cumulativePaise.isEmpty()) return@Canvas
        val path = Path().apply {
            moveTo(0f, size.height)
            cumulativePaise.forEachIndexed { index, paise -> lineTo(x(index + 1), y(paise)) }
        }
        drawPath(path, spentColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(spentColor, radius = 4.dp.toPx(), center = Offset(x(cumulativePaise.size), y(cumulativePaise.last())))
    }
}

/** Monthly totals as bars, the last (current) month highlighted; [budgetPaise] as a dashed line. */
@Composable
fun MonthBars(totalsPaise: List<Long>, budgetPaise: Long?, color: Color, modifier: Modifier = Modifier) {
    val muted = color.copy(alpha = 0.35f)
    val budgetColor = MaterialTheme.colorScheme.error
    Canvas(modifier) {
        if (totalsPaise.isEmpty()) return@Canvas
        val top = maxOf(totalsPaise.max(), budgetPaise ?: 0L, 1L).toFloat()
        val slot = size.width / totalsPaise.size
        val barWidth = slot * 0.55f
        totalsPaise.forEachIndexed { index, paise ->
            val height = size.height * (paise.coerceAtLeast(0) / top)
            drawRoundRect(
                color = if (index == totalsPaise.lastIndex) color else muted,
                topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
        }
        budgetPaise?.let {
            val y = size.height - size.height * (it / top)
            drawLine(
                budgetColor,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}
