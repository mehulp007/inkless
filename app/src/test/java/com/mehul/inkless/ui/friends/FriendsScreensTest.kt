package com.mehul.inkless.ui.friends

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.FriendTotals
import com.mehul.inkless.splits.Settled
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.theme.InklessTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Friends tab and a friend's page, with fixed balances instead of their view models. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1600dp-xxhdpi")
class FriendsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun at(day: Int) = LocalDate.of(2026, 11, day).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val rohan = FriendEntity("r", "Rohan Mehta", listOf("rohan@okaxis"), "rohan-uid", "rohan_m", "me", 0)
    private val priya = FriendEntity("p", "Priya Nair", emptyList(), null, null, "me", 0)
    private val aditya = FriendEntity("a", "Aditya", emptyList(), null, null, "me", 0)

    private val swiggy = ShareLine("1", "s1", "r", 30_000, false, "t1", at(5), "Swiggy")
    private val auto = IouEntity("i1", "r", 15_000, true, "Auto to the station", at(4), false, null, "me", 0)
    private val rohanBalance = FriendBalance(rohan, 45_000, listOf(swiggy), listOf(auto))
    private val balances = listOf(
        rohanBalance,
        FriendBalance(priya, -12_000, emptyList(), listOf(IouEntity("i2", "p", 12_000, false, "Movie tickets", at(2), false, null, "me", 0))),
        FriendBalance(aditya, 0, emptyList(), emptyList()),
    )
    private val incoming = listOf(IncomingSplitEntity("x", "kiran-uid", "Kiran", 90_000, 30_000, false, "Dinner at Night Canteen", at(6)))

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
    }

    @Composable
    private fun Tab(onAdd: (String, List<String>, String?) -> Unit = { _, _, _ -> }, list: List<FriendBalance> = balances) {
        FriendsContent(
            balances = list, incoming = incoming, totals = FriendTotals.of(list), form = FriendForm(), message = null,
            onOpenFriend = {}, onAddFriend = onAdd, onFormClosed = {}, onMarkPaid = {}, onMessageShown = {},
        )
    }

    @Composable
    private fun Page(
        settled: List<Settled> = emptyList(),
        onSettleShare: (ShareLine) -> Unit = {},
        onSettleIou: (IouEntity) -> Unit = {},
        onSettleAll: () -> Unit = {},
    ) {
        FriendContent(
            balance = rohanBalance, settled = settled, form = FriendForm(), message = null,
            onBack = {}, onOpenTransaction = {}, onRemind = {}, onSettleAll = onSettleAll, onSettleShare = onSettleShare,
            onMarkIncomingPaid = {}, onAddIou = { _, _, _ -> }, onEditIou = { _, _, _, _ -> }, onSettleIou = onSettleIou,
            onDeleteIou = {}, onEdit = { _, _, _ -> }, onFormClosed = {}, onRemove = {}, onMessageShown = {},
        )
    }

    @Test
    fun friendsTab() {
        show { Tab() }
        // Owed in total, and by Rohan, who is the only one who owes anything.
        assertThat(compose.onAllNodesWithText("₹450").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("Owes you · @rohan_m").assertExists()
        // The total you owe, and Priya's row.
        assertThat(compose.onAllNodesWithText("You owe").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("All square").assertExists()
        compose.onNodeWithText("Kiran paid ₹900").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun friendsTabDark() {
        show(dark = true) { Tab() }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun noFriendsYet() {
        show { Tab(list = emptyList()) }
        compose.onNodeWithText("No friends yet").assertExists()
    }

    @Test
    fun addingAFriend() {
        var added: Triple<String, List<String>, String?>? = null
        show { Tab(onAdd = { n, v, u -> added = Triple(n, v, u) }) }
        compose.onNodeWithText("Add a friend").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Kiran Rao")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("kiran@ybl, kiran.rao@okhdfc")
        compose.onNode(hasText("Add friend") and hasClickAction()).performClick()
        assertThat(added).isEqualTo(Triple("Kiran Rao", listOf("kiran@ybl", "kiran.rao@okhdfc"), null))
    }

    @Test
    fun friendPage() {
        show { Page(settled = listOf(Settled.Share(ShareLine("2", "s2", "r", 12_000, true, "t2", at(1), "Dominos")))) }
        compose.onNodeWithText("Rohan owes you, all told.").assertExists()
        compose.onNodeWithText("Settled · 1").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun settlingOneThingAtATime() {
        var share: ShareLine? = null
        var iou: IouEntity? = null
        var all = false
        show { Page(onSettleShare = { share = it }, onSettleIou = { iou = it }, onSettleAll = { all = true }) }
        compose.onNode(hasText("Paid back") and hasClickAction()).performClick()
        assertThat(share).isEqualTo(swiggy)

        compose.onNodeWithText("Auto to the station").performClick()
        compose.onNodeWithText("Mark paid back").performClick()
        assertThat(iou).isEqualTo(auto)

        // Settling everything asks first.
        compose.onNode(hasText("Settle all") and hasClickAction()).performClick()
        assertThat(all).isFalse()
        compose.onNodeWithText("Settle everything with Rohan Mehta?").assertExists()
    }

    @Test
    fun balanceWordsAndSentences() {
        assertThat(balanceWords(15_000)).isEqualTo(BalanceWords("₹150", "owes you", ChipTone.Good))
        assertThat(balanceWords(-8_000)).isEqualTo(BalanceWords("₹80", "you owe", ChipTone.Check))
        assertThat(balanceWords(0)).isEqualTo(BalanceWords("", "all square", ChipTone.Neutral))
        assertThat(friendSentence("Priya Nair", -1)).isEqualTo("You owe Priya, all told.")
        assertThat(friendSentence("Aditya", 0)).isEqualTo("Nothing open between you.")
    }
}
