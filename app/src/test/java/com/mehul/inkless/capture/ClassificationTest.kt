package com.mehul.inkless.capture

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import org.junit.Test

class ClassificationTest {

    private fun account(value: String, role: AccountRole, match: AccountMatch = AccountMatch.UPI_ID) =
        MyAccountEntity(value, match, value, role, null, "u1", 0)

    private val accounts = listOf(
        account("mehul@oksbi", AccountRole.MINE),
        account("MEHUL PATIL", AccountRole.MINE, AccountMatch.NAME),
        account("papa@okhdfcbank", AccountRole.HOME),
    )

    private fun txn(
        direction: Direction,
        amount: Long = 50000,
        vpa: String? = null,
        payee: String? = null,
        status: TransactionStatus = TransactionStatus.NEEDS_REVIEW,
    ) = TransactionEntity(
        id = "t", amountPaise = amount, direction = direction,
        type = if (direction == Direction.DEBIT) TransactionType.EXPENSE else TransactionType.INCOME,
        timestamp = 1L, vpa = vpa, payeeRaw = payee, merchantId = "m1",
        categoryId = if (direction == Direction.DEBIT) CategoryIds.OTHER else null,
        status = status, sources = setOf(CaptureSource.SMS), createdAt = 1L, updatedAt = 1L, ownerUid = "u1",
    )

    @Test
    fun `moving money to my own account is a transfer, not spending`() {
        val toMine = Classification.apply(txn(Direction.DEBIT, payee = "MEHUL PATIL"), accounts, null)
        assertThat(toMine.type).isEqualTo(TransactionType.SELF_TRANSFER)
        assertThat(toMine.categoryId).isNull()

        val fromMine = Classification.apply(txn(Direction.CREDIT, vpa = "mehul@oksbi"), accounts, null)
        assertThat(fromMine.type).isEqualTo(TransactionType.SELF_TRANSFER)
    }

    @Test
    fun `name matching needs every word, in any order`() {
        assertThat(Classification.roleOf(txn(Direction.DEBIT, payee = "PATIL MEHUL G"), accounts)).isEqualTo(AccountRole.MINE)
        assertThat(Classification.roleOf(txn(Direction.DEBIT, payee = "MEHUL SHARMA"), accounts)).isNull()
    }

    @Test
    fun `masked upi ids still match`() {
        val masked = listOf(account("rohan1265@upi", AccountRole.HOME))
        assertThat(Classification.roleOf(txn(Direction.CREDIT, vpa = "*1265@upi"), masked)).isEqualTo(AccountRole.HOME)
    }

    @Test
    fun `money from home is tagged as allowance`() {
        val allowance = Classification.apply(txn(Direction.CREDIT, vpa = "papa@okhdfcbank"), accounts, null)
        assertThat(allowance.type).isEqualTo(TransactionType.INCOME)
        assertThat(allowance.tags).contains(Classification.ALLOWANCE_TAG)
    }

    @Test
    fun `money back from a merchant paid as much recently is a refund in that category`() {
        val earlier = txn(Direction.DEBIT, amount = 79900, status = TransactionStatus.AUTO).copy(categoryId = CategoryIds.SHOPPING)
        val refund = Classification.apply(txn(Direction.CREDIT, amount = 79900), emptyList(), earlier)
        assertThat(refund.type).isEqualTo(TransactionType.REFUND)
        assertThat(refund.categoryId).isEqualTo(CategoryIds.SHOPPING)
        assertThat(refund.status).isEqualTo(TransactionStatus.AUTO)

        // More back than was ever paid isn't a refund.
        val more = Classification.apply(txn(Direction.CREDIT, amount = 90000), emptyList(), earlier)
        assertThat(more.type).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun `what the user confirmed is left alone`() {
        val confirmed = txn(Direction.DEBIT, payee = "MEHUL PATIL", status = TransactionStatus.CONFIRMED)
        assertThat(Classification.apply(confirmed, accounts, null)).isEqualTo(confirmed)
    }

    @Test
    fun `money back for one item of a broken-down order goes to that item's category`() {
        val order = txn(Direction.DEBIT, amount = 500_000).copy(status = TransactionStatus.CONFIRMED, categoryId = CategoryIds.SHOPPING)
        val items = listOf(
            DraftItem(300_000, CategoryIds.SHOPPING),
            DraftItem(150_000, CategoryIds.COLLEGE),
            DraftItem(50_000, CategoryIds.HEALTH),
        )
        val refund = Classification.apply(txn(Direction.CREDIT, amount = 150_000), accounts, order, items)
        assertThat(refund.type).isEqualTo(TransactionType.REFUND)
        assertThat(refund.categoryId).isEqualTo(CategoryIds.COLLEGE)
        assertThat(refund.status).isEqualTo(TransactionStatus.AUTO)
    }

    @Test
    fun `money back that matches no item of a broken-down order is asked about`() {
        val order = txn(Direction.DEBIT, amount = 500_000).copy(status = TransactionStatus.CONFIRMED, categoryId = CategoryIds.SHOPPING)
        val items = listOf(DraftItem(300_000, CategoryIds.SHOPPING), DraftItem(200_000, CategoryIds.COLLEGE))
        val refund = Classification.apply(txn(Direction.CREDIT, amount = 120_000), accounts, order, items)
        assertThat(refund.type).isEqualTo(TransactionType.REFUND)
        assertThat(refund.status).isEqualTo(TransactionStatus.NEEDS_REVIEW)
    }
}
