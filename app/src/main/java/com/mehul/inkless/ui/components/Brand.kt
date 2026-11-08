package com.mehul.inkless.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** The ledger drop on a green rounded square: the app's mark, as on the launcher icon. */
@Composable
fun BrandTile(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.small, color = Inkless.colors.primary) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.logo_mark),
                contentDescription = null,
                tint = Inkless.colors.onPrimary,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** The mark with "inkless" next to it, as at the top of Welcome. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        BrandTile()
        Text("inkless", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Inkless.colors.ink)
    }
}
