package com.mehul.inkless.ui.split

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import org.junit.Test

class SplitStateTest {

    private val payment = TransactionEntity(
        id = "t1",
        amountPaise = 600_00,
        direction = Direction.DEBIT,
        type = TransactionType.EXPENSE,
        timestamp = 0,
        status = TransactionStatus.CONFIRMED,
        sources = setOf(CaptureSource.SMS),
        createdAt = 0,
        updatedAt = 0,
        ownerUid = "u1",
    )
    private val state = SplitState(loading = false, transaction = payment, picked = listOf("a", "b"))

    @Test
    fun `equal leaves the user a third`() {
        assertThat(state.myPaise).isEqualTo(200_00)
        assertThat(state.problem).isNull()
    }

    @Test
    fun `typed amounts must all be there and fit`() {
        val exact = state.copy(method = SplitMethod.EXACT, typed = mapOf("a" to "250"))
        assertThat(exact.problem).isNotNull()
        assertThat(exact.copy(typed = mapOf("a" to "250", "b" to "150.50")).myPaise).isEqualTo(199_50)
        assertThat(exact.copy(typed = mapOf("a" to "500", "b" to "200")).shares).isNull()
    }

    @Test
    fun `percentages are read to two decimals`() {
        val percent = state.copy(method = SplitMethod.PERCENT, typed = mapOf("a" to "33.33", "b" to "50"))
        assertThat(percent.shares?.map { it.basisPoints }).containsExactly(3_333, 5_000)
        assertThat(percent.shares?.map { it.sharePaise }).containsExactly(199_98L, 300_00L)
        assertThat(percent.copy(typed = mapOf("a" to "60", "b" to "50")).problem).isNotNull()
    }

    @Test
    fun `nobody picked is a problem`() {
        assertThat(state.copy(picked = emptyList()).problem).isEqualTo("Pick who you're splitting with.")
    }
}
