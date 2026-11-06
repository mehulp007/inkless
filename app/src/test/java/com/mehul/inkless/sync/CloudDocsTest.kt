package com.mehul.inkless.sync

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import org.junit.Test

class CloudDocsTest {

    private val transaction = TransactionEntity(
        id = "t1",
        amountPaise = 4500,
        direction = Direction.DEBIT,
        type = TransactionType.EXPENSE,
        timestamp = 1_000,
        vpa = "q829154@ybl",
        payeeRaw = "Tapri near Block C",
        merchantId = "m1",
        accountLast4 = "1234",
        bankCode = "SBI",
        upiRef = "512300000001",
        balanceAfterPaise = 120_000,
        categoryId = "food_canteen",
        confidence = 0.85f,
        status = TransactionStatus.AUTO,
        sources = setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION),
        note = "with Rohan",
        tags = listOf("allowance"),
        createdAt = 1_000,
        updatedAt = 2_000,
        ownerUid = "u1",
        syncState = SyncState.SYNCED,
    )

    // What Firestore hands back: whole numbers as Long, decimals as Double.
    private fun asFirestore(map: Map<String, Any?>): Map<String, Any?> = map.mapValues { (_, value) ->
        when (value) {
            is Int -> value.toLong()
            is Float -> value.toDouble()
            else -> value
        }
    }

    @Test
    fun `a transaction comes back exactly as it went up`() {
        val back = TransactionDoc.fromMap("t1", asFirestore(TransactionDoc(transaction).toMap()), "u1")
        assertThat(back?.transaction).isEqualTo(transaction)
    }

    @Test
    fun `the document holds parsed fields only, never the owner or sync state`() {
        val doc = TransactionDoc(transaction).toMap()
        assertThat(doc.keys).containsNoneOf("ownerUid", "syncState", "receiptUri", "body", "id")
    }

    @Test
    fun `an account number goes up as its last four digits`() {
        val doc = TransactionDoc(transaction.copy(accountLast4 = "50100123451234")).toMap()
        assertThat(doc["accountLast4"]).isEqualTo("1234")
    }

    @Test
    fun `fields a newer version added are ignored`() {
        val doc = TransactionDoc(transaction).toMap() + ("somethingNew" to listOf(1L, 2L))
        assertThat(TransactionDoc.fromMap("t1", asFirestore(doc), "u1")?.transaction).isEqualTo(transaction)
    }

    @Test
    fun `a document this version can't read is skipped, not guessed at`() {
        val unknownType = TransactionDoc(transaction).toMap() + ("type" to "SOMETHING_NEW")
        val noAmount = TransactionDoc(transaction).toMap() - "amountPaise"
        assertThat(TransactionDoc.fromMap("t1", unknownType, "u1")).isNull()
        assertThat(TransactionDoc.fromMap("t1", noAmount, "u1")).isNull()
    }

    @Test
    fun `a broken-down payment brings its items along, in order`() {
        val items = listOf(
            TransactionItemEntity("i1", "t1", 3_000, "shopping", "shoes", 0, "u1"),
            TransactionItemEntity("i2", "t1", 1_500, "college", null, 1, "u1"),
        )
        val back = TransactionDoc.fromMap("t1", asFirestore(TransactionDoc(transaction, items.reversed()).toMap()), "u1")
        assertThat(back?.items).isEqualTo(items)
    }

    @Test
    fun `a deletion comes back as a deleted row`() {
        val doc = TransactionDoc(transaction.copy(deleted = true)).toMap()
        assertThat(TransactionDoc.fromMap("t1", asFirestore(doc), "u1")?.transaction?.deleted).isTrue()
    }

    @Test
    fun `categories, merchants, rules and accounts round trip too`() {
        val category = CategoryEntity("food_canteen", "Food & Canteen", "🍔", 0xFFF29D38, 300_000, 0, true, "u1", 5, syncState = SyncState.SYNCED)
        val merchant = MerchantEntity("m1", "q829154@ybl", "Tapri", "food_canteen", 3, "u1", 5, syncState = SyncState.SYNCED)
        val rule = RuleEntity("r1", RuleField.UPI_ID, RuleMatch.CONTAINS, "canteen", "food_canteen", 1, "u1", 5, syncState = SyncState.SYNCED)
        val account = MyAccountEntity("a1", AccountMatch.NAME, "MEHUL PATIL", AccountRole.MINE, null, "u1", 5, syncState = SyncState.SYNCED)

        assertThat(CloudDocs.category("food_canteen", asFirestore(CloudDocs.category(category)), "u1")).isEqualTo(category)
        assertThat(CloudDocs.merchant("m1", asFirestore(CloudDocs.merchant(merchant)), "u1")).isEqualTo(merchant)
        assertThat(CloudDocs.rule("r1", asFirestore(CloudDocs.rule(rule)), "u1")).isEqualTo(rule)
        assertThat(CloudDocs.myAccount("a1", asFirestore(CloudDocs.myAccount(account)), "u1")).isEqualTo(account)
    }

    @Test
    fun `rows that come down are stored as already synced`() {
        val doc = TransactionDoc(transaction.copy(syncState = SyncState.DIRTY)).toMap()
        assertThat(TransactionDoc.fromMap("t1", asFirestore(doc), "u1")?.transaction?.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a budget round trips`() {
        val budget = BudgetEntity("monthly", BudgetPeriod.MONTH, 1_200_000, 5, 20_000, "u1", 9, syncState = SyncState.SYNCED)
        assertThat(CloudDocs.budget("monthly", asFirestore(CloudDocs.budget(budget)), "u1")).isEqualTo(budget)
        val noDay = budget.copy(allowanceDay = null, dailyFoodCapPaise = null)
        assertThat(CloudDocs.budget("monthly", asFirestore(CloudDocs.budget(noDay)), "u1")).isEqualTo(noDay)
    }
}
