package com.mehul.inkless.ui.components

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.R
import com.mehul.inkless.data.seed.DefaultCategories
import org.junit.Test

class CategoryIconsTest {

    @Test
    fun everyDefaultCategoryHasItsOwnIcon() {
        val icons = DefaultCategories.create(ownerUid = "me", nowMillis = 0).map { CategoryIcons.forCategory(it.id) }
        assertThat(icons).doesNotContain(R.drawable.icon_tags)
        assertThat(icons.toSet()).hasSize(icons.size)
    }

    @Test
    fun customCategoriesGetTheTagIcon() {
        assertThat(CategoryIcons.forCategory("my_own_thing")).isEqualTo(R.drawable.icon_tags)
    }

    @Test
    fun moneyMovementsHaveTheirOwnIcons() {
        assertThat(CategoryIcons.forPayment("travel", isCredit = false, isTransfer = true)).isEqualTo(R.drawable.icon_arrow_left_right)
        assertThat(CategoryIcons.forPayment(null, isCredit = true, isTransfer = false)).isEqualTo(R.drawable.icon_arrow_down_left)
        assertThat(CategoryIcons.forPayment("food_delivery", isCredit = true, isTransfer = false)).isEqualTo(R.drawable.icon_undo_2)
        assertThat(CategoryIcons.forPayment("food_canteen", isCredit = false, isTransfer = false)).isEqualTo(R.drawable.icon_utensils)
        assertThat(CategoryIcons.forPayment(null, isCredit = false, isTransfer = false)).isEqualTo(R.drawable.icon_scan_line)
    }

    @Test
    fun initialsAreTheFirstLetterWithAFullStop() {
        assertThat(initialOf("hostel_ledger")).isEqualTo("h.")
        assertThat(initialOf("@Rohan")).isEqualTo("r.")
        assertThat(initialOf("  ")).isEqualTo("?")
    }
}
