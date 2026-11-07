package com.mehul.inkless.ui.transaction

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RawCaptureStatus
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import com.mehul.inkless.parsing.OrderLine
import com.mehul.inkless.parsing.ParsedOrder
import com.mehul.inkless.share.ShareContent
import com.mehul.inkless.share.ShareUi
import com.mehul.inkless.ui.breakdown.BreakdownContent
import com.mehul.inkless.ui.breakdown.BreakdownState
import com.mehul.inkless.ui.breakdown.ItemLine
import com.mehul.inkless.ui.capture.UnreadContent
import com.mehul.inkless.ui.friends.FriendForm
import com.mehul.inkless.ui.review.ReviewContent
import com.mehul.inkless.ui.review.SortCard
import com.mehul.inkless.ui.split.SplitContent
import com.mehul.inkless.ui.split.SplitState
import com.mehul.inkless.ui.theme.InklessTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** To sort, a payment, Break down, a shared order, a split and the unread messages, without their view models. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1800dp-xxhdpi")
class PaymentScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val categories = DefaultCategories.create("u1", 0)
    private fun category(id: String) = categories.single { it.id == id }
    private val zone = ZoneId.systemDefault()
    private fun at(day: Int, hour: Int, minute: Int) = LocalDate.of(2026, 11, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val blinkit = TransactionEntity(
        id = "b", amountPaise = 42_000, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = at(5, 18, 26),
        payeeRaw = "Blinkit", accountLast4 = "4821", bankCode = "SBI", upiRef = "849625796058",
        categoryId = CategoryIds.GROCERIES, status = TransactionStatus.CONFIRMED,
        sources = setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION), note = "Room snacks + a notebook for class",
        createdAt = 0, updatedAt = 0, ownerUid = "u1",
    )
    private val items = listOf(
        TransactionItemEntity("i1", "b", 9_000, CategoryIds.GROCERIES, "Milk · 2 packs", 0, "u1"),
        TransactionItemEntity("i2", "b", 6_000, CategoryIds.GROCERIES, "Bananas", 1, "u1"),
        TransactionItemEntity("i3", "b", 15_000, CategoryIds.GROCERIES, "Room snacks", 2, "u1"),
        TransactionItemEntity("i4", "b", 12_000, CategoryIds.COLLEGE, "A4 notebook", 3, "u1"),
    )

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
    }

    private fun waiting(id: String, name: String, rupees: Long, hour: Int, minute: Int) = TransactionListItem(
        id = id, amountPaise = rupees * 100, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = at(6, hour, minute),
        categoryId = CategoryIds.OTHER, vpa = null, payeeRaw = name, merchantName = name, status = TransactionStatus.NEEDS_REVIEW,
        sources = setOf(CaptureSource.SMS),
    )

    @Test
    fun toSort() {
        var picked: Pair<String, String>? = null
        val cards = listOf(
            SortCard(waiting("q", "Campus QR · 8291", 50, 17, 40), listOf(category(CategoryIds.FOOD_CANTEEN), category(CategoryIds.COLLEGE), category(CategoryIds.TRAVEL))),
            SortCard(waiting("h", "Hostel QR · 4102", 30, 18, 15), listOf(category(CategoryIds.FOOD_CANTEEN), category(CategoryIds.LAUNDRY), category(CategoryIds.GROCERIES))),
        )
        show { ReviewContent(cards, categories, onPick = { id, c -> picked = id to c }, onKeep = {}, onOpenTransaction = {}) }
        compose.onNodeWithText("2 left").assertExists()
        compose.onNodeWithText("6 Nov · 17:40 · Bank SMS").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Laundry & Hostel").performClick()
        assertThat(picked).isEqualTo("h" to CategoryIds.LAUNDRY)
    }

    @Test
    fun allSorted() {
        show { ReviewContent(emptyList(), categories, onPick = { _, _ -> }, onKeep = {}, onOpenTransaction = {}) }
        compose.onNodeWithText("All sorted.").assertExists()
    }

    @Test
    fun paymentBrokenDown() {
        val state = TransactionScreenState(loading = false, transaction = blinkit, categories = categories, items = items)
        show { TransactionContent(state, onBack = {}, onSave = { _, _ -> }, onRename = {}, onBreakDown = {}, onRemoveBreakdown = {}, onSplit = {}, onDelete = {}) }
        compose.onNodeWithText("Expense · 1 payment, 4 items").assertExists()
        compose.onNodeWithText("SBI •• 4821").assertExists()
        compose.onNodeWithText("•••• 6058").assertExists()
        compose.onNodeWithText("Groceries & Essentials").assertExists()
        compose.onNodeWithText("₹300 here · ₹120 in College & Stationery").assertExists()
        compose.onNodeWithText("4 items · exact total ₹420").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun paymentToSortStartsOnNothingAndDeletesAfterAsking() {
        var deleted = false
        val waitingTxn = blinkit.copy(status = TransactionStatus.NEEDS_REVIEW, categoryId = CategoryIds.OTHER, note = null)
        val state = TransactionScreenState(loading = false, transaction = waitingTxn, categories = categories)
        show(dark = true) { TransactionContent(state, onBack = {}, onSave = { _, _ -> }, onRename = {}, onBreakDown = {}, onRemoveBreakdown = {}, onSplit = {}, onDelete = { deleted = true }) }
        compose.onNodeWithText("Expense · to sort").assertExists()
        compose.onNodeWithText("Several things in one payment?").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Delete this entry").performScrollTo().performClick()
        assertThat(deleted).isFalse()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        assertThat(deleted).isTrue()
    }

    @Test
    fun breakDown() {
        val state = BreakdownState(
            loading = false,
            transaction = blinkit,
            categories = categories,
            lines = listOf(
                ItemLine(0, "90", CategoryIds.GROCERIES, "Milk · 2 packs"),
                ItemLine(1, "60", CategoryIds.GROCERIES, "Bananas"),
                ItemLine(2, "150", CategoryIds.GROCERIES, "Room snacks"),
            ),
            lastCategoryId = CategoryIds.COLLEGE,
            lastNote = "A4 notebook",
        )
        show {
            BreakdownContent(state, onBack = {}, onAmount = { _, _ -> }, onCategory = { _, _ -> }, onName = { _, _ -> }, onAdd = {}, onRemove = {}, onLastCategory = {}, onLastName = {}, onSave = {})
        }
        compose.onNodeWithText("₹120.00").assertExists()
        compose.onNodeWithText("₹0.00 left · matches payment exactly").assertExists()
        compose.onNode(hasText("Save breakdown") and hasClickAction()).assertIsEnabled()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun breakDownThatAddsUpToTooMuch() {
        val state = BreakdownState(
            loading = false, transaction = blinkit, categories = categories,
            lines = listOf(ItemLine(0, "500", CategoryIds.GROCERIES, "Too much")), lastCategoryId = CategoryIds.COLLEGE,
        )
        show {
            BreakdownContent(state, onBack = {}, onAmount = { _, _ -> }, onCategory = { _, _ -> }, onName = { _, _ -> }, onAdd = {}, onRemove = {}, onLastCategory = {}, onLastName = {}, onSave = {})
        }
        compose.onNodeWithText("The items add up to more than the payment").assertExists()
        compose.onNode(hasText("Save breakdown") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun sharedOrder() {
        val order = ParsedOrder(
            listOf(OrderLine("Milk · 2 packs", 9_000), OrderLine("Bananas", 6_000), OrderLine("Room snacks", 15_000), OrderLine("A4 notebook", 12_000)),
            totalPaise = 42_000,
        )
        val ui = ShareUi.Ready(
            order = order,
            items = order.items.map { DraftItem(it.amountPaise, if (it.name.contains("notebook")) CategoryIds.COLLEGE else CategoryIds.GROCERIES, it.name) },
            payments = listOf(blinkit),
            selectedId = "b",
            matchedId = "b",
        )
        val names = categories.associate { it.id to it.name }
        var confirmed = false
        show { ShareContent(ui, names, sharedFrom = "Blinkit", onSelect = {}, onClose = {}, onContinue = { confirmed = true }) }
        compose.onNodeWithText("Shared from Blinkit").assertExists()
        compose.onNodeWithText("Matched existing payment".uppercase()).assertExists()
        compose.onNodeWithText("Total · matches payment").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Confirm items").performScrollTo().performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun split() {
        val friends = listOf(
            FriendEntity("f1", "Rohan Mehta", ownerUid = "u1", updatedAt = 0),
            FriendEntity("f2", "Priya Nair", ownerUid = "u1", updatedAt = 0),
            FriendEntity("f3", "Aditya", ownerUid = "u1", updatedAt = 0),
        )
        val dinner = blinkit.copy(id = "d", payeeRaw = "Night Canteen", amountPaise = 90_000, categoryId = CategoryIds.FOOD_CANTEEN)
        val state = SplitState(loading = false, transaction = dinner, friends = friends, picked = listOf("f1", "f2"), method = SplitMethod.EQUAL)
        show {
            SplitContent(state, FriendForm(), onBack = {}, onToggle = {}, onMethod = {}, onType = { _, _ -> }, onAddFriend = { _, _, _ -> }, onFormClosed = {}, onSave = {}, onRemove = {})
        }
        compose.onNodeWithText("Your part").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun unreadMessages() {
        val captures = listOf(
            RawCaptureEntity("r1", CaptureSource.SMS, "VM-HDFCBK", "h1", "Rs.250.00 debited from a/c **4821 on 05-11-26 towards UPI/CANTEENMESS. Avl bal Rs 21,342.50", at(5, 13, 2), RawCaptureStatus.UNPARSED),
            RawCaptureEntity("r2", CaptureSource.SMS, "AX-SBIUPI", "h2", "Your a/c XX4821 is debited by INR 80 on 04Nov. Ref 6058.", at(4, 9, 41), RawCaptureStatus.UNPARSED),
        )
        show { UnreadContent(captures, categories, onBack = {}, onDismiss = {}, onAdd = { _, _, _, _, _ -> }) }
        compose.onNodeWithText("Messages we couldn't read.").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onAllNodes(hasText("Add as a payment") and hasClickAction())[0].performClick()
        compose.onNodeWithText("Add this payment.").assertExists()
    }
}
