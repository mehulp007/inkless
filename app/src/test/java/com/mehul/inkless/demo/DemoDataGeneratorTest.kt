package com.mehul.inkless.demo

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.DefaultCategories
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DemoDataGeneratorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 6, 19, 30, 0, 0, zone)
    private val data = DemoDataGenerator().generate(now)

    @Test
    fun `same seed gives the same data`() {
        val again = DemoDataGenerator().generate(now)
        assertThat(again).isEqualTo(data)
    }

    @Test
    fun `different seed gives different data`() {
        val other = DemoDataGenerator(seed = 7).generate(now)
        assertThat(other.transactions).isNotEqualTo(data.transactions)
    }

    @Test
    fun `covers this month and the two before it, never the future`() {
        val days = data.transactions.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        assertThat(days.min()).isAtLeast(now.toLocalDate().withDayOfMonth(1).minusMonths(2))
        assertThat(data.transactions.maxOf { it.timestamp }).isAtMost(now.toInstant().toEpochMilli())
        assertThat(days.map { it.month }.toSet()).hasSize(3)
    }

    @Test
    fun `allowance arrives once a month`() {
        val allowances = data.transactions.filter { it.type == TransactionType.INCOME }
        assertThat(allowances).hasSize(3)
        assertThat(allowances.all { it.direction == Direction.CREDIT && it.categoryId == null }).isTrue()
    }

    @Test
    fun `every expense uses a built-in category`() {
        val categoryIds = DefaultCategories.create("demo", 0).map { it.id }.toSet()
        val expenses = data.transactions.filter { it.type == TransactionType.EXPENSE }
        assertThat(expenses).isNotEmpty()
        expenses.forEach { assertThat(it.categoryId).isIn(categoryIds) }
    }

    @Test
    fun `amounts are positive and ids unique`() {
        assertThat(data.transactions.all { it.amountPaise > 0 }).isTrue()
        assertThat(data.transactions.map { it.id }.toSet()).hasSize(data.transactions.size)
    }

    @Test
    fun `every transaction points at a merchant`() {
        val merchantIds = data.merchants.map { it.id }.toSet()
        data.transactions.forEach { assertThat(it.merchantId).isIn(merchantIds) }
        assertThat(data.merchants.sumOf { it.timesSeen }).isEqualTo(data.transactions.size)
    }

    @Test
    fun `looks like a hostel month, not a millionaire's`() {
        val firstFullMonth = now.toLocalDate().withDayOfMonth(1).minusMonths(1)
        val spentPaise = data.transactions
            .filter { it.direction == Direction.DEBIT }
            .filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().withDayOfMonth(1) == firstFullMonth }
            .sumOf { it.amountPaise }
        assertThat(spentPaise).isIn(Range.closed(6_000_00L, 16_000_00L))
    }
}
