package com.mehul.inkless.ui.budget

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.components.rememberGrowth
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
    val shown = rememberGrowth(fraction.coerceIn(0f, 1f))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }) {
            val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(fill, -90f, 360f * shown.value, false, Offset(inset, inset), arcSize, style = stroke)
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
    val summary = ChartText.pace(cumulativePaise, budgetPaise, cycleDays, pacePaisePerDay)
    Canvas(modifier.semantics { contentDescription = summary }) {
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
    val summary = ChartText.months(totalsPaise, budgetPaise)
    Canvas(modifier.semantics { contentDescription = summary }) {
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

/** What the charts say, read out by TalkBack in place of the drawing. */
object ChartText {

    /** "₹2,700 spent by day 6 of 30, of ₹12,000. At the recent pace, about ₹13,500 by the end." */
    fun pace(cumulativePaise: List<Long>, budgetPaise: Long, cycleDays: Int, pacePaisePerDay: Long): String {
        if (cycleDays <= 0 || budgetPaise <= 0) return "No allowance set"
        val days = cumulativePaise.size
        val now = cumulativePaise.lastOrNull() ?: 0L
        val start = "${Money.format(now.coerceAtLeast(0))} spent by day $days of $cycleDays, of ${Money.format(budgetPaise)}."
        if (days !in 1 until cycleDays || pacePaisePerDay <= 0) return start
        val end = now + pacePaisePerDay * (cycleDays - days)
        return "$start At the recent pace, about ${Money.format(end / 100 * 100)} by the end."
    }

    /** "Monthly totals, oldest first: ₹900, ₹1,200. This month ₹780. Budget ₹3,000." */
    fun months(totalsPaise: List<Long>, budgetPaise: Long?): String {
        if (totalsPaise.isEmpty()) return "No months yet"
        val earlier = totalsPaise.dropLast(1)
        val parts = buildList {
            if (earlier.isNotEmpty()) add("Monthly totals, oldest first: ${earlier.joinToString { Money.format(it.coerceAtLeast(0)) }}.")
            add("This month ${Money.format(totalsPaise.last().coerceAtLeast(0))}.")
            budgetPaise?.let { add("Budget ${Money.format(it)}.") }
        }
        return parts.joinToString(" ")
    }
}
