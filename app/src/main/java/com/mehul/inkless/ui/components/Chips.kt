package com.mehul.inkless.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/**
 * A scrolling row of pills where one is picked: Home's categories ("All", "Food & Canteen", …).
 * The picked pill is filled green, the others sit on the raised surface.
 */
@Composable
fun FilterPills(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Space before the first pill and after the last, inside the scrolling row. */
    edgePadding: Dp = 0.dp,
) {
    val colors = Inkless.colors
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = edgePadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                shape = CircleShape,
                color = if (isSelected) colors.primary else colors.surface,
                contentColor = if (isSelected) colors.onPrimary else colors.muted,
                border = if (isSelected) null else BorderStroke(1.dp, colors.hairline),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .selectable(isSelected, role = Role.Tab, onClick = { onSelect(index) }),
            ) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * Equal-width boxes where one is picked: "1 month · 3 months · 6 months", "Equal · Exact · %",
 * "Light · Dark · System". The picked one gets the green tint and a green outline.
 */
@Composable
fun PillChoice(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Inkless.colors
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) colors.tint else colors.surface,
                contentColor = if (isSelected) colors.primary else colors.muted,
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.hairline),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(index) }),
            ) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** What a [StatusChip] is saying. */
enum class ChipTone { Good, Check, Neutral, Danger }

/** A small rounded label: "Allowed", "Blocked", "Optional", "26% used", "2 left". */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.Good) {
    val colors = Inkless.colors
    val (background, foreground) = when (tone) {
        ChipTone.Good -> colors.tint to colors.primary
        ChipTone.Check -> colors.warm to colors.onWarm
        ChipTone.Neutral -> colors.tint to colors.muted
        ChipTone.Danger -> colors.dangerTint to colors.danger
    }
    Surface(modifier, shape = CircleShape, color = background, contentColor = foreground) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
