package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** A sheet that slides up over the screen: adding cash, picking a category, adding a friend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InklessBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = Inkless.colors.background,
        contentColor = Inkless.colors.ink,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        dragHandle = {
            Box(Modifier.padding(top = Spacing.m, bottom = Spacing.s).size(width = 40.dp, height = 4.dp).background(Inkless.colors.tintStrong, CircleShape))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

/** "Are you sure?" with a plain cancel and a confirm that's red when it can't be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Inkless.colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Inkless.colors.ink) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.muted) },
        confirmButton = {
            TextLink(confirmText, onConfirm, color = if (destructive) Inkless.colors.danger else Inkless.colors.primary)
        },
        dismissButton = { TextLink(dismissText, onDismiss, color = Inkless.colors.muted) },
    )
}

/** A friendly placeholder where a list would be: an icon, a line saying what goes here, an action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.icon_inbox,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxl, horizontal = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(icon, size = 56.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            TextLink(action, onAction)
        }
    }
}
