package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** A page's serif headline with an optional muted line under it: "Good to have you back." */
@Composable
fun PageHeadline(
    title: String,
    modifier: Modifier = Modifier,
    lead: String? = null,
    style: TextStyle = MaterialTheme.typography.displayMedium,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(title, style = style, color = Inkless.colors.ink, modifier = Modifier.semantics { heading() })
        if (lead != null) {
            Text(lead, style = MaterialTheme.typography.bodyLarge, color = Inkless.colors.muted)
        }
    }
}

/** A section title inside a page, with an optional green action on the right ("Manage", "Edit all"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = Inkless.colors.ink,
        )
        if (action != null && onAction != null) {
            TextLink(action, onAction)
        }
    }
}

/** Small spaced-out capitals: "HISTORY TO IMPORT", "YOUR ALLOWANCE CYCLE". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = Inkless.type.overline, color = Inkless.colors.muted)
}

/** Small centred print at the bottom of a screen. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = Inkless.colors.muted,
        textAlign = TextAlign.Center,
    )
}

/** An icon with a bold line and an explanation: "Raw alert text stays on your phone." */
@Composable
fun PrivacyNote(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    @DrawableRes icon: Int = R.drawable.icon_shield_check,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Inkless.colors.primary,
            modifier = Modifier.padding(top = 1.dp).size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.muted)
            }
        }
    }
}
