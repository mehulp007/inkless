package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless

/**
 * The bar at the top of every screen: a back arrow (or a small icon on a tab's first screen), the
 * title, and an optional step marker ("1 OF 3", "DRAFT") or action icons on the right.
 */
@Composable
fun InklessTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    @DrawableRes icon: Int? = null,
    marker: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(62.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.icon_arrow_left), contentDescription = "Back", tint = Inkless.colors.ink)
            }
            icon != null -> Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Inkless.colors.ink,
                modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
            )
            else -> Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = Inkless.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (marker != null) {
            Text(marker.uppercase(), style = Inkless.type.overline, color = Inkless.colors.muted, modifier = Modifier.padding(horizontal = 16.dp))
        }
        actions()
    }
}
