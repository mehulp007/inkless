package com.mehul.inkless.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.ui.theme.Inkless

/** Every category as a tappable chip with its icon; wraps onto as many lines as it needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Inkless.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(painterResource(CategoryIcons.forCategory(category.id)), contentDescription = null, modifier = Modifier.size(18.dp))
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surface,
                    labelColor = colors.ink,
                    iconColor = colors.primary,
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    selectedLeadingIconColor = colors.onPrimary,
                ),
                border = if (selected) null else BorderStroke(1.dp, colors.hairline),
            )
        }
    }
}
