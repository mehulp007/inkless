package com.mehul.inkless.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mehul.inkless.ui.theme.Inkless

/** A ring filling up as the budget is spent; it turns red past 100%. */
@Composable
fun SpendRing(fraction: Float, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val track = Inkless.colors.tintStrong
    val fill = if (fraction > 1f) Inkless.colors.danger else Inkless.colors.primary
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
 * Spending so far (solid), where it's heading at the recent pace (dashed), and an even pace that
 * ends exactly at the budget (thin). [label] sits by today's point: "6 Nov · ₹2,700".
 */
@Composable
fun PaceChart(
    cumulativePaise: List<Long>,
    budgetPaise: Long,
    cycleDays: Int,
    modifier: Modifier = Modifier,
    pacePaisePerDay: Long = 0,
    label: String? = null,
) {
    val spentColor = Inkless.colors.primary
    val paceColor = Inkless.colors.muted
    val tagColor = Inkless.colors.tint
    val guide = Inkless.colors.hairline
    val measurer = rememberTextMeasurer()
    val tagStyle = MaterialTheme.typography.labelSmall.copy(color = Inkless.colors.primary)
    Canvas(modifier) {
        if (cycleDays <= 0 || budgetPaise <= 0) return@Canvas
        val days = cumulativePaise.size
        val now = cumulativePaise.lastOrNull() ?: 0L
        val forecastEnd = if (days in 1 until cycleDays && pacePaisePerDay > 0) now + pacePaisePerDay * (cycleDays - days) else null
        val top = maxOf(budgetPaise, cumulativePaise.maxOrNull() ?: 0L, forecastEnd ?: 0L).toFloat()
        fun x(day: Int) = size.width * day / cycleDays
        fun y(paise: Long) = size.height - size.height * (paise.coerceAtLeast(0) / top)

        // Faint guides at half and the whole budget.
        listOf(budgetPaise / 2, budgetPaise).forEach { level ->
            drawLine(guide, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
        }
        drawLine(paceColor, start = Offset(0f, size.height), end = Offset(size.width, y(budgetPaise)), strokeWidth = 1.5.dp.toPx())
        if (cumulativePaise.isEmpty()) return@Canvas
        if (forecastEnd != null) {
            drawLine(
                spentColor,
                start = Offset(x(days), y(now)),
                end = Offset(size.width, y(forecastEnd)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
            )
        }
        val path = Path().apply {
            moveTo(0f, size.height)
            cumulativePaise.forEachIndexed { index, paise -> lineTo(x(index + 1), y(paise)) }
        }
        drawPath(path, spentColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val point = Offset(x(days), y(now))
        drawCircle(spentColor, radius = 4.dp.toPx(), center = point)

        if (label != null) {
            val text = measurer.measure(label, tagStyle)
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val w = text.size.width + padX * 2
            val h = text.size.height + padY * 2
            // Above the point, kept inside the chart.
            val left = (point.x - w / 2).coerceIn(0f, size.width - w)
            val topY = (point.y - h - 8.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(tagColor, topLeft = Offset(left, topY), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            drawText(text, topLeft = Offset(left + padX, topY + padY))
        }
    }
}

/** Monthly totals as bars, the last (current) month in dark green; [budgetPaise] as a dashed line. */
@Composable
fun MonthBars(totalsPaise: List<Long>, budgetPaise: Long?, modifier: Modifier = Modifier) {
    val color = Inkless.colors.primary
    val muted = Inkless.colors.tintStrong
    val budgetColor = Inkless.colors.danger
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
                cornerRadius = CornerRadius(6.dp.toPx()),
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
