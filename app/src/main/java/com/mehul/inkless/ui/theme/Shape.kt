package com.mehul.inkless.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val InklessShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    // Icon tiles.
    small = RoundedCornerShape(14.dp),
    // Text fields and small cards.
    medium = RoundedCornerShape(16.dp),
    // Cards.
    large = RoundedCornerShape(20.dp),
    // Bottom sheets and dialogs.
    extraLarge = RoundedCornerShape(28.dp),
)
