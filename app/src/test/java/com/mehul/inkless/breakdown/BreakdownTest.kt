package com.mehul.inkless.breakdown

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.seed.CategoryIds
import org.junit.Test

class BreakdownTest {

    private val order = 500_000L // ₹5,000

    private fun item(rupees: Long, category: String? = CategoryIds.SHOPPING) = DraftItem(rupees * 100, category)

    @Test
    fun `the last item takes exactly what's left, to the paisa`() {
        val others = listOf(item(1_000), item(1_000), DraftItem(99_950, CategoryIds.COLLEGE))
        assertThat(Breakdown.rest(order, others)).isEqualTo(200_050)
        val items = Breakdown.complete(order, others, CategoryIds.GROCERIES, "the rest")!!
        assertThat(items.sumOf { it.amountPaise }).isEqualTo(order)
        assertThat(items.last()).isEqualTo(DraftItem(200_050, CategoryIds.GROCERIES, "the rest"))
    }

    @Test
    fun `five things of a thousand each`() {
        val others = List(4) { item(1_000) }
        val items = Breakdown.complete(order, others, CategoryIds.HEALTH, null)!!
        assertThat(items.map { it.amountPaise }).containsExactly(100_000L, 100_000L, 100_000L, 100_000L, 100_000L)
        assertThat(Breakdown.isValid(order, items.map { it.amountPaise })).isTrue()
    }

    @Test
    fun `says what's wrong until it's a real breakdown`() {
        assertThat(Breakdown.problem(order, emptyList(), CategoryIds.OTHER)).isEqualTo("Add at least two items")
        assertThat(Breakdown.problem(order, listOf(item(0)), CategoryIds.OTHER)).isEqualTo("Every item needs an amount")
        assertThat(Breakdown.problem(order, listOf(item(6_000)), CategoryIds.OTHER)).isEqualTo("The items add up to more than the payment")
        assertThat(Breakdown.problem(order, listOf(item(5_000)), CategoryIds.OTHER)).isEqualTo("Nothing is left for the last item")
        assertThat(Breakdown.problem(order, listOf(item(1_000, category = null)), CategoryIds.OTHER)).isEqualTo("Pick a category for every item")
        assertThat(Breakdown.problem(order, listOf(item(1_000)), null)).isEqualTo("Pick a category for every item")
        assertThat(Breakdown.problem(order, listOf(item(1_000)), CategoryIds.OTHER)).isNull()
        assertThat(Breakdown.complete(order, listOf(item(6_000)), CategoryIds.OTHER, null)).isNull()
    }

    @Test
    fun `saved items must add up and be more than one`() {
        assertThat(Breakdown.isValid(order, listOf(order))).isFalse()
        assertThat(Breakdown.isValid(order, listOf(400_000, 99_999))).isFalse()
        assertThat(Breakdown.isValid(order, listOf(500_000, 0))).isFalse()
        assertThat(Breakdown.isValid(order, listOf(400_000, 100_000))).isTrue()
    }

    @Test
    fun `listed under the biggest item, the first added when two tie`() {
        val items = listOf(item(1_000, "a"), item(2_000, "b"), item(2_000, "c"))
        assertThat(Breakdown.primary(items) { it.amountPaise }?.categoryId).isEqualTo("b")
    }

    @Test
    fun `a refund goes to the item of the same amount, or nowhere if that isn't clear`() {
        val items = listOf(item(1_000, CategoryIds.SHOPPING), item(1_500, CategoryIds.COLLEGE), item(2_500, CategoryIds.HEALTH))
        assertThat(Breakdown.refundCategory(150_000, items)).isEqualTo(CategoryIds.COLLEGE)
        assertThat(Breakdown.refundCategory(120_000, items)).isNull()
        val twoAlike = listOf(item(1_000, CategoryIds.SHOPPING), item(1_000, CategoryIds.FUN))
        assertThat(Breakdown.refundCategory(100_000, twoAlike)).isNull()
        val sameCategory = listOf(item(1_000, CategoryIds.FUN), item(1_000, CategoryIds.FUN))
        assertThat(Breakdown.refundCategory(100_000, sameCategory)).isEqualTo(CategoryIds.FUN)
    }
}
