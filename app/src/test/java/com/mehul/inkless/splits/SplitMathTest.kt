package com.mehul.inkless.splits

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import org.junit.Test

class SplitMathTest {

    @Test
    fun `an equal split leaves the odd paise with the payer`() {
        // ₹100 between three: friends ₹33.33 each, the payer ₹33.34.
        val shares = SplitMath.equal(10_000, listOf("rohan", "priya"))
        assertThat(shares.map { it.sharePaise }).containsExactly(3_333L, 3_333L)
        assertThat(SplitMath.myShare(10_000, shares)).isEqualTo(3_334)
        assertThat(SplitMath.equal(60_000, listOf("a", "b")).map { it.sharePaise }).containsExactly(20_000L, 20_000L)
        assertThat(SplitMath.equal(60_000, emptyList())).isEmpty()
    }

    @Test
    fun `exact amounts may not come to more than the payment`() {
        assertThat(SplitMath.exact(60_000, mapOf("rohan" to 25_000L, "priya" to 15_000L))?.sumOf { it.sharePaise }).isEqualTo(40_000)
        assertThat(SplitMath.exact(60_000, mapOf("rohan" to 60_000L))).isNotNull() // lent the whole thing
        assertThat(SplitMath.exact(60_000, mapOf("rohan" to 50_000L, "priya" to 20_000L))).isNull()
        assertThat(SplitMath.exact(60_000, mapOf("rohan" to 0L))).isNull()
    }

    @Test
    fun `percentages round down and the payer keeps the rest`() {
        val shares = SplitMath.percent(10_001, mapOf("rohan" to 3_333, "priya" to 3_333))!!
        assertThat(shares.map { it.sharePaise }).containsExactly(3_333L, 3_333L)
        assertThat(shares.first().basisPoints).isEqualTo(3_333)
        assertThat(SplitMath.myShare(10_001, shares)).isEqualTo(3_335)
        assertThat(SplitMath.percent(10_000, mapOf("rohan" to 6_000, "priya" to 5_000))).isNull()
    }

    private fun friend(id: String, name: String, vpas: List<String> = emptyList(), uid: String? = null) =
        FriendEntity(id, name, vpas, uid, null, "me", 0)

    private fun line(id: String, friend: String, paise: Long, settled: Boolean = false) =
        ShareLine(id, "s$id", friend, paise, settled, "t$id", 0, "Swiggy")

    private fun iou(id: String, friend: String, paise: Long, theyOwe: Boolean, settled: Boolean = false) =
        IouEntity(id, friend, paise, theyOwe, null, 0, settled, null, "me", 0)

    @Test
    fun `a friend's balance adds their shares and loans and takes off what the user owes them`() {
        val rohan = friend("r", "Rohan Kumar", uid = "rohan-uid")
        val priya = friend("p", "Priya")
        val balances = Balances.of(
            friends = listOf(rohan, priya),
            shares = listOf(line("1", "r", 20_000), line("2", "r", 5_000, settled = true), line("3", "p", 15_000)),
            ious = listOf(iou("a", "r", 10_000, theyOwe = true), iou("b", "p", 5_000, theyOwe = false)),
            incoming = listOf(
                IncomingSplitEntity("x", "rohan-uid", "rohan", 60_000, 12_000, false, null, 0),
                IncomingSplitEntity("y", "rohan-uid", "rohan", 60_000, 9_999, true, null, 0),
            ),
        ).associate { it.friend.id to it.netPaise }
        assertThat(balances).containsExactly("r", 18_000L, "p", 10_000L)
    }

    private fun credit(paise: Long, vpa: String? = null, payee: String? = null) = TransactionEntity(
        id = "c", amountPaise = paise, direction = Direction.CREDIT, type = TransactionType.INCOME, timestamp = 0,
        vpa = vpa, payeeRaw = payee, status = TransactionStatus.AUTO, sources = setOf(CaptureSource.SMS),
        createdAt = 0, updatedAt = 0, ownerUid = "me",
    )

    @Test
    fun `a repayment is matched to the friend by UPI ID or by name`() {
        val friends = listOf(friend("r", "Rohan Kumar", listOf("rohan.k@okaxis")), friend("p", "Priya Shah"))
        assertThat(SettleMatch.friendFor(credit(1, vpa = "rohan.k@okaxis"), friends)?.id).isEqualTo("r")
        assertThat(SettleMatch.friendFor(credit(1, vpa = "*n.k@okaxis"), friends)?.id).isEqualTo("r")
        assertThat(SettleMatch.friendFor(credit(1, payee = "PRIYA SHAH"), friends)?.id).isEqualTo("p")
        assertThat(SettleMatch.friendFor(credit(1, payee = "PRIYA MEHTA"), friends)).isNull()
    }

    @Test
    fun `a repayment settles the share of that amount, or everything when it's the whole lot`() {
        val shares = listOf(line("1", "r", 15_000), line("2", "r", 20_000))
        val ious = listOf(iou("a", "r", 5_000, theyOwe = true), iou("b", "r", 9_000, theyOwe = false))
        assertThat(SettleMatch.targets(20_000, shares, ious)).containsExactly(SettleTarget.Share(shares[1]))
        assertThat(SettleMatch.targets(5_000, shares, ious)).containsExactly(SettleTarget.Iou(ious[0]))
        assertThat(SettleMatch.targets(40_000, shares, ious)).hasSize(3)
        assertThat(SettleMatch.targets(12_345, shares, ious)).isNull()
    }
}
