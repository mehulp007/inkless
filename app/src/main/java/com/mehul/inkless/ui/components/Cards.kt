package com.mehul.inkless.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** A raised card: the light surface with a hairline border. Tappable when [onClick] is set. */
@Composable
fun InklessCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Inkless.colors
    KitCard(modifier, onClick, colors.surface, colors.ink, BorderStroke(1.dp, colors.hairline), padding, content)
}

/** A green-tinted card for good news and explanations: safe to spend, privacy promises. */
@Composable
fun TintCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Inkless.colors
    KitCard(modifier, onClick, colors.tint, colors.ink, null, padding, content)
}

/** A beige card for something that needs a look: verify your email, a blocked permission. */
@Composable
fun WarmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Inkless.colors
    KitCard(modifier, onClick, colors.warm, colors.onWarm, null, padding, content)
}

@Composable
private fun KitCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    color: Color,
    contentColor: Color,
    border: BorderStroke?,
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
    }
    if (onClick != null) {
        Surface(onClick, modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor, border = border, content = inner)
    } else {
        Surface(modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor, border = border, content = inner)
    }
}
