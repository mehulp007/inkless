package com.mehul.inkless.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: InklessDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            InklessDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun txn(
        id: String,
        amount: Long,
        timestamp: Long,
        categoryId: String?,
        direction: Direction = Direction.DEBIT,
        type: TransactionType = TransactionType.EXPENSE,
        deleted: Boolean = false,
        merchantId: String? = null,
    ) = TransactionEntity(
        id = id,
        amountPaise = amount,
        direction = direction,
        type = type,
        timestamp = timestamp,
        merchantId = merchantId,
        categoryId = categoryId,
        status = TransactionStatus.AUTO,
        sources = setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION),
        tags = listOf("hostel"),
        createdAt = timestamp,
        updatedAt = timestamp,
        ownerUid = "test",
        deleted = deleted,
    )

    @Test
    fun categoryTotalsSubtractRefundsAndSkipDeletedAndOutOfRange() = runTest {
        db.categoryDao().insertAll(DefaultCategories.create("test", 0))
        db.transactionDao().insertAll(
            listOf(
                txn("a", 1000_00, 100, CategoryIds.SHOPPING),
                txn("b", 300_00, 200, CategoryIds.SHOPPING, Direction.CREDIT, TransactionType.REFUND),
                txn("c", 45_00, 300, CategoryIds.FOOD_CANTEEN),
                txn("d", 99_00, 400, CategoryIds.FOOD_CANTEEN, deleted = true),
                txn("e", 12000_00, 500, null, Direction.CREDIT, TransactionType.INCOME),
                txn("f", 500_00, 1000, CategoryIds.FOOD_CANTEEN),
            ),
        )

        db.transactionDao().observeCategoryTotals(startMillis = 0, endMillis = 1000).test {
            val totals = awaitItem().associate { it.categoryId to it.totalPaise }
            assertThat(totals).containsExactly(
                CategoryIds.SHOPPING, 700_00L,
                CategoryIds.FOOD_CANTEEN, 45_00L,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listJoinsMerchantNameAndSortsNewestFirst() = runTest {
        db.merchantDao().insertAll(
            listOf(MerchantEntity(id = "m1", vpa = "q829@ybl", displayName = "Tapri", ownerUid = "test", updatedAt = 0)),
        )
        db.transactionDao().insertAll(
            listOf(
                txn("old", 20_00, 100, CategoryIds.FOOD_CANTEEN, merchantId = "m1"),
                txn("new", 30_00, 200, CategoryIds.FOOD_CANTEEN),
            ),
        )

        db.transactionDao().observeBetween(0, 1000).test {
            val items = awaitItem()
            assertThat(items.map { it.id }).containsExactly("new", "old").inOrder()
            assertThat(items.last().merchantName).isEqualTo("Tapri")
            assertThat(items.first().merchantName).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun convertersRoundTripSourcesAndTags() = runTest {
        db.transactionDao().insertAll(listOf(txn("x", 10_00, 100, null)))
        val stored = db.query("SELECT sources, tags FROM transactions", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0) to cursor.getString(1)
        }
        assertThat(stored).isEqualTo("SMS,NOTIFICATION" to """["hostel"]""")
    }

    @Test
    fun aBrokenDownPaymentCountsEachItemInItsOwnCategory() = runTest {
        db.categoryDao().insertAll(DefaultCategories.create("test", 0))
        db.transactionDao().insertAll(
            listOf(
                txn("order", 5000_00, 100, CategoryIds.SHOPPING),
                txn("shoes-back", 1000_00, 200, CategoryIds.SHOPPING, Direction.CREDIT, TransactionType.REFUND),
                txn("chai", 45_00, 300, CategoryIds.FOOD_CANTEEN),
            ),
        )
        db.transactionItemDao().replace(
            "order",
            listOf(
                TransactionItemEntity("i1", "order", 3000_00, CategoryIds.SHOPPING, "shoes", 0, "test"),
                TransactionItemEntity("i2", "order", 1500_00, CategoryIds.COLLEGE, "calculator", 1, "test"),
                TransactionItemEntity("i3", "order", 500_00, CategoryIds.HEALTH, null, 2, "test"),
            ),
        )

        db.transactionDao().observeCategoryTotals(startMillis = 0, endMillis = 1000).test {
            assertThat(awaitItem().associate { it.categoryId to it.totalPaise }).containsExactly(
                CategoryIds.SHOPPING, 2000_00L,
                CategoryIds.COLLEGE, 1500_00L,
                CategoryIds.HEALTH, 500_00L,
                CategoryIds.FOOD_CANTEEN, 45_00L,
            )
        }
        db.transactionDao().observeBetween(0, 1000).test {
            assertThat(awaitItem().first { it.id == "order" }.itemCount).isEqualTo(3)
        }

        // Back to one category: the whole payment counts there again.
        db.transactionItemDao().replace("order", emptyList())
        db.transactionDao().observeCategoryTotals(startMillis = 0, endMillis = 1000).test {
            assertThat(awaitItem().associate { it.categoryId to it.totalPaise }[CategoryIds.SHOPPING]).isEqualTo(4000_00L)
        }
    }

    @Test
    fun theSpendViewSplitsItemsAndLeavesOutWhatIsNotSpending() = runTest {
        db.transactionDao().insertAll(
            listOf(
                txn("order", 5000_00, 100, CategoryIds.SHOPPING),
                txn("back", 1000_00, 200, CategoryIds.SHOPPING, Direction.CREDIT, TransactionType.REFUND),
                txn("home", 12000_00, 300, null, Direction.CREDIT, TransactionType.INCOME),
                txn("mine", 2000_00, 400, null, type = TransactionType.SELF_TRANSFER),
                txn("gone", 99_00, 500, CategoryIds.FOOD_CANTEEN, deleted = true),
            ),
        )
        db.transactionItemDao().replace(
            "order",
            listOf(
                TransactionItemEntity("i1", "order", 3000_00, CategoryIds.SHOPPING, null, 0, "test"),
                TransactionItemEntity("i2", "order", 2000_00, CategoryIds.COLLEGE, null, 1, "test"),
            ),
        )
        val parts = db.spendDao().between(0, 1000)
        assertThat(parts.map { it.categoryId to it.amountPaise }).containsExactly(
            CategoryIds.SHOPPING to 3000_00L,
            CategoryIds.COLLEGE to 2000_00L,
            CategoryIds.SHOPPING to -1000_00L,
        )
    }

    @Test
    fun aSplitPaymentCountsOnlyTheUsersPart() = runTest {
        db.transactionDao().insertAll(
            listOf(
                txn("dinner", 900_00, 100, CategoryIds.FOOD_DELIVERY),
                txn("cab", 300_00, 200, CategoryIds.TRAVEL),
            ),
        )
        val dao = db.friendDao()
        dao.saveSplit(SplitEntity("s1", "dinner", SplitMethod.EQUAL, ownerUid = "test", updatedAt = 1))
        dao.replaceShares(
            "s1",
            listOf(
                SplitShareEntity("a", "s1", "f1", 300_00, ownerUid = "test"),
                SplitShareEntity("b", "s1", "f2", 300_00, settled = true, ownerUid = "test"),
            ),
        )
        // A removed split counts in full again.
        dao.saveSplit(SplitEntity("s2", "cab", SplitMethod.EQUAL, ownerUid = "test", updatedAt = 1, deleted = true))
        dao.replaceShares("s2", listOf(SplitShareEntity("c", "s2", "f1", 150_00, ownerUid = "test")))

        assertThat(db.spendDao().between(0, 1000).associate { it.categoryId to it.amountPaise }).containsExactly(
            CategoryIds.FOOD_DELIVERY, 300_00L,
            CategoryIds.TRAVEL, 300_00L,
        )
    }
}
