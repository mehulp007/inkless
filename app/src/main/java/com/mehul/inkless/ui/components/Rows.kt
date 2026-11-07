package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/**
 * One line of the ledger: a tile, the merchant, a detail line ("Food & Canteen · 13:10") with an
 * optional chip under it ("+3 more items"), and the amount on the right.
 */
@Composable
fun LedgerRow(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    amountColor: Color = Inkless.colors.ink,
    leading: @Composable () -> Unit = {},
    chip: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Inkless.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (chip != null) {
                Row(Modifier.padding(top = Spacing.xs)) { chip() }
            }
        }
        Text(amount, style = Inkless.type.moneyRow, color = amountColor, textAlign = TextAlign.End)
    }
}

/** "Today · Fri, 6 Nov" on the left and the day's total in green on the right. */
@Composable
fun DayHeader(label: String, modifier: Modifier = Modifier, total: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = Inkless.colors.ink,
        )
        if (total != null) {
            Text(total, style = MaterialTheme.typography.labelLarge, color = Inkless.colors.primary)
        }
    }
}

/** A fact with its label: "Date … 5 Nov 2026 · 18:26". */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.ink, textAlign = TextAlign.End)
    }
}

/** A setting you switch on or off, with an explanation under it. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = Inkless.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) colors.ink else colors.muted)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onPrimary,
                checkedBorderColor = colors.primary,
                uncheckedTrackColor = colors.tintStrong,
                uncheckedThumbColor = colors.surface,
                uncheckedBorderColor = colors.tintStrong,
            ),
        )
    }
}

/**
 * A tappable line that leads somewhere: a settings entry, an export, an action. Destructive ones
 * ("Delete account") are red; disabled ones are muted.
 */
@Composable
fun NavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Inkless.colors
    val titleColor = when {
        !enabled -> colors.muted
        destructive -> colors.danger
        else -> colors.ink
    }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = if (destructive) colors.danger else colors.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        if (showChevron && !destructive) {
            Icon(painterResource(R.drawable.icon_chevron_right), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}
