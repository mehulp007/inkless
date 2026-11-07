package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
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
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Lora

/** A line icon on a small green-tinted square, as next to every payment in a list. */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.small, color = Inkless.colors.tint) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = contentDescription,
                tint = Inkless.colors.primary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** The tile for a payment: its category's icon, or what kind of money movement it was. */
@Composable
fun CategoryTile(
    categoryId: String?,
    modifier: Modifier = Modifier,
    isCredit: Boolean = false,
    isTransfer: Boolean = false,
    size: Dp = 40.dp,
) {
    IconTile(CategoryIcons.forPayment(categoryId, isCredit, isTransfer), modifier, size)
}

/** A person or account as the first letter on a tile, the way Figma shows "h." for the profile. */
@Composable
fun InitialTile(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.small, color = Inkless.colors.tint) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initialOf(name),
                fontFamily = Lora,
                fontSize = (size.value * 0.42f).sp,
                color = Inkless.colors.primary,
            )
        }
    }
}

internal fun initialOf(name: String): String =
    name.trim().trimStart('@').firstOrNull { it.isLetterOrDigit() }?.lowercase()?.plus(".") ?: "?"

/** Which line icon stands for which category, matching the icons in the Figma design. */
object CategoryIcons {

    @DrawableRes
    fun forCategory(categoryId: String?): Int = when (categoryId) {
        CategoryIds.FOOD_CANTEEN -> R.drawable.icon_utensils
        CategoryIds.FOOD_DELIVERY -> R.drawable.icon_soup
        CategoryIds.GROCERIES -> R.drawable.icon_shopping_basket
        CategoryIds.TRAVEL -> R.drawable.icon_car_front
        CategoryIds.RECHARGE -> R.drawable.icon_smartphone
        CategoryIds.COLLEGE -> R.drawable.icon_notebook
        CategoryIds.LAUNDRY -> R.drawable.icon_shirt
        CategoryIds.FUN -> R.drawable.icon_party_popper
        CategoryIds.SHOPPING -> R.drawable.icon_shopping_bag
        CategoryIds.HEALTH -> R.drawable.icon_pill
        CategoryIds.GIFTS -> R.drawable.icon_gift
        CategoryIds.LENT -> R.drawable.icon_hand_coins
        CategoryIds.OTHER, null -> R.drawable.icon_scan_line
        // Categories the user made themselves.
        else -> R.drawable.icon_tags
    }

    /**
     * A payment's tile. Money between your own accounts and money coming in get their own icons;
     * a refund keeps its category's place in the ledger but shows as money coming back.
     */
    @DrawableRes
    fun forPayment(categoryId: String?, isCredit: Boolean, isTransfer: Boolean): Int = when {
        isTransfer -> R.drawable.icon_arrow_left_right
        isCredit && categoryId != null -> R.drawable.icon_undo_2
        isCredit -> R.drawable.icon_arrow_down_left
        else -> forCategory(categoryId)
    }
}
