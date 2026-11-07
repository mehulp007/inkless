package com.mehul.inkless.splits

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import org.junit.Test

class FriendHistoryTest {

    private fun friend(id: String) = FriendEntity(id, id, emptyList(), null, null, "me", 0)

    private fun line(id: String, friend: String, paise: Long, at: Long, settled: Boolean) =
        ShareLine(id, "s$id", friend, paise, settled, "t$id", at, "Swiggy")

    private fun iou(id: String, friend: String, paise: Long, theyOwe: Boolean, at: Long, settled: Boolean = false, deleted: Boolean = false) =
        IouEntity(id, friend, paise, theyOwe, null, at, settled, null, "me", 0, deleted)

    @Test
    fun `totals add up what everyone owes you and what you owe`() {
        val balances = listOf(
            FriendBalance(friend("r"), 45_000, emptyList(), emptyList()),
            FriendBalance(friend("p"), -12_000, emptyList(), emptyList()),
            FriendBalance(friend("a"), 0, emptyList(), emptyList()),
            FriendBalance(friend("k"), 5_000, emptyList(), emptyList()),
        )
        assertThat(FriendTotals.of(balances)).isEqualTo(FriendTotals(owedToYouPaise = 50_000, youOwePaise = 12_000))
        assertThat(FriendTotals.of(emptyList())).isEqualTo(FriendTotals(0, 0))
    }

    @Test
    fun `settled history is one friend's settled shares and entries, newest first`() {
        val history = Settled.of(
            friendId = "r",
            lines = listOf(
                line("1", "r", 20_000, at = 10, settled = true),
                line("2", "r", 5_000, at = 30, settled = false),
                line("3", "p", 9_000, at = 40, settled = true),
            ),
            ious = listOf(
                iou("a", "r", 15_000, theyOwe = true, at = 20, settled = true),
                iou("b", "r", 8_000, theyOwe = false, at = 50, settled = true, deleted = true),
                iou("c", "r", 3_000, theyOwe = true, at = 60, settled = false),
            ),
        )
        assertThat(history.map { it.timestamp }).containsExactly(20L, 10L).inOrder()
        assertThat((history[0] as Settled.Iou).iou.id).isEqualTo("a")
        assertThat((history[1] as Settled.Share).line.shareId).isEqualTo("1")
    }
}
