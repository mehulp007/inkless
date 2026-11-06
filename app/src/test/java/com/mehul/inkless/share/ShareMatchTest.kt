package com.mehul.inkless.share

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.OrderLine
import com.mehul.inkless.parsing.ParsedOrder
import org.junit.Test

class ShareMatchTest {

    private fun payment(id: String, paise: Long, at: Long) = TransactionEntity(
        id = id, amountPaise = paise, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = at,
        status = TransactionStatus.AUTO, sources = setOf(CaptureSource.SMS), createdAt = at, updatedAt = at, ownerUid = "u1",
    )

    private val payments = listOf(payment("chai", 4_500, 30), payment("amazon", 205_800, 20), payment("older", 99_900, 10))
    private val order = ParsedOrder(
        items = listOf(OrderLine("Classmate Notebook Pack of 6", 36_000), OrderLine("boAt Rockerz 450 Headphones", 149_900)),
        totalPaise = 205_800,
    )

    @Test
    fun `the payment for the order's total comes first and is picked`() {
        assertThat(ShareMatch.rank(payments, order).map { it.id }).containsExactly("amazon", "chai", "older").inOrder()
        assertThat(ShareMatch.bestMatch(payments, order)?.id).isEqualTo("amazon")
    }

    @Test
    fun `without a total on the page, the items' sum is matched`() {
        val noTotal = order.copy(totalPaise = null, items = order.items + OrderLine("Dettol Soap", 19_900))
        assertThat(ShareMatch.bestMatch(payments, noTotal)?.id).isEqualTo("amazon")
    }

    @Test
    fun `nothing is picked when no payment, or more than one, has that amount`() {
        assertThat(ShareMatch.bestMatch(payments, order.copy(totalPaise = 1))).isNull()
        assertThat(ShareMatch.bestMatch(payments + payment("again", 205_800, 5), order)).isNull()
    }

    @Test
    fun `items become draft lines named after them, with a category guess`() {
        val draft = ShareMatch.draft(order)
        assertThat(draft.map { it.note }).containsExactly("Classmate Notebook Pack of 6", "boAt Rockerz 450 Headphones").inOrder()
        assertThat(draft.map { it.categoryId }).containsExactly(CategoryIds.COLLEGE, CategoryIds.SHOPPING).inOrder()
        assertThat(ShareMatch.guessCategory("Allen Solly Polo T-Shirt")).isEqualTo(CategoryIds.SHOPPING)
        assertThat(ShareMatch.guessCategory("Amul Taaza Toned Fresh Milk")).isEqualTo(CategoryIds.GROCERIES)
        assertThat(ShareMatch.guessCategory("Something Unusual")).isNull()
    }
}
