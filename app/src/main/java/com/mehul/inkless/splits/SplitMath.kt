package com.mehul.inkless.splits

import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.TransactionEntity

/** A friend's part of a split before it's saved. */
data class ShareDraft(val friendId: String, val sharePaise: Long, val basisPoints: Int? = null)

/**
 * Dividing a payment between the user (who paid) and friends, to the paisa. Whatever doesn't
 * divide evenly stays with the payer, so friends never owe a fraction of a paisa more than their part.
 */
object SplitMath {

    /** Everyone, the payer included, pays the same; the payer keeps the remainder. */
    fun equal(totalPaise: Long, friendIds: List<String>): List<ShareDraft> {
        if (friendIds.isEmpty()) return emptyList()
        val each = totalPaise / (friendIds.size + 1)
        return friendIds.map { ShareDraft(it, each) }
    }

    /** Amounts typed per friend. Null if one is missing or they come to more than the payment. */
    fun exact(totalPaise: Long, amounts: Map<String, Long>): List<ShareDraft>? {
        if (amounts.isEmpty() || amounts.values.any { it <= 0 } || amounts.values.sum() > totalPaise) return null
        return amounts.map { (id, paise) -> ShareDraft(id, paise) }
    }

    /** Percentages per friend in hundredths (2500 = 25%), each rounded down. Null past 100% in all. */
    fun percent(totalPaise: Long, basisPoints: Map<String, Int>): List<ShareDraft>? {
        if (basisPoints.isEmpty() || basisPoints.values.any { it <= 0 } || basisPoints.values.sum() > FULL) return null
        return basisPoints.map { (id, bp) -> ShareDraft(id, totalPaise * bp / FULL, bp) }
    }

    fun myShare(totalPaise: Long, shares: List<ShareDraft>): Long = totalPaise - shares.sumOf { it.sharePaise }

    const val FULL = 10_000
}

/** Where things stand with one friend; positive when they owe the user. */
data class FriendBalance(
    val friend: FriendEntity,
    val netPaise: Long,
    val openShares: List<ShareLine>,
    val openIous: List<IouEntity>,
    /** Splits the friend paid for and shared, where the user hasn't paid their part yet. */
    val theirSplits: List<IncomingSplitEntity> = emptyList(),
)

object Balances {

    /**
     * Each friend's balance: their unsettled shares and IOUs they owe add up, IOUs the user owes
     * and unsettled splits the friend paid for (shared from their app) take away.
     */
    fun of(
        friends: List<FriendEntity>,
        shares: List<ShareLine>,
        ious: List<IouEntity>,
        incoming: List<IncomingSplitEntity>,
    ): List<FriendBalance> = friends.map { friend ->
        val openShares = shares.filter { it.friendId == friend.id && !it.settled }
        val openIous = ious.filter { it.friendId == friend.id && !it.settled && !it.deleted }
        val theirSplits = friend.linkedUid?.let { uid -> incoming.filter { it.payerUid == uid && !it.settled } }.orEmpty()
        val net = openShares.sumOf { it.sharePaise } +
            openIous.sumOf { if (it.theyOwe) it.amountPaise else -it.amountPaise } -
            theirSplits.sumOf { it.mySharePaise }
        FriendBalance(friend, net, openShares, openIous, theirSplits)
    }
}

/** Across every friend: what they owe you all together, and what you owe them. */
data class FriendTotals(val owedToYouPaise: Long, val youOwePaise: Long) {
    companion object {
        fun of(balances: List<FriendBalance>) = FriendTotals(
            owedToYouPaise = balances.sumOf { it.netPaise.coerceAtLeast(0) },
            youOwePaise = balances.sumOf { (-it.netPaise).coerceAtLeast(0) },
        )
    }
}

/** Something already squared up with a friend, for their page's history. */
sealed interface Settled {
    val timestamp: Long

    data class Share(val line: ShareLine) : Settled {
        override val timestamp get() = line.timestamp
    }

    data class Iou(val iou: IouEntity) : Settled {
        override val timestamp get() = iou.timestamp
    }

    companion object {
        /** [friendId]'s settled shares and entries, newest first. */
        fun of(friendId: String, lines: List<ShareLine>, ious: List<IouEntity>): List<Settled> =
            (lines.filter { it.friendId == friendId && it.settled }.map(::Share) +
                ious.filter { it.friendId == friendId && it.settled && !it.deleted }.map(::Iou))
                .sortedByDescending { it.timestamp }
    }
}

/** What a repayment from a friend most likely pays back. */
sealed interface SettleTarget {
    data class Share(val line: ShareLine) : SettleTarget
    data class Iou(val iou: IouEntity) : SettleTarget
}

object SettleMatch {

    private val word = Regex("[a-z0-9]+")

    /** The friend a payment came from, by UPI ID (allowing for masked ones) or by all of their name's words. */
    fun friendFor(txn: TransactionEntity, friends: List<FriendEntity>): FriendEntity? {
        val vpa = txn.vpa?.lowercase()
        friends.firstOrNull { friend ->
            vpa != null && friend.vpas.any { mine ->
                val known = mine.trim().lowercase()
                known == vpa || (vpa.startsWith('*') && known.endsWith(vpa.removePrefix("*")))
            }
        }?.let { return it }
        val payee = txn.payeeRaw?.lowercase()?.let { p -> word.findAll(p).map { it.value }.toSet() } ?: return null
        return friends.firstOrNull { friend ->
            val name = word.findAll(friend.name.lowercase()).map { it.value }.toList()
            name.isNotEmpty() && name.all { it in payee }
        }
    }

    /**
     * What [creditPaise] settles: the oldest single thing of exactly that amount, or everything
     * the friend owes when it's exactly the total. Null when it's not clear.
     */
    fun targets(creditPaise: Long, shares: List<ShareLine>, ious: List<IouEntity>): List<SettleTarget>? {
        val owed = shares.filter { !it.settled }.map { SettleTarget.Share(it) to it.sharePaise } +
            ious.filter { it.theyOwe && !it.settled }.map { SettleTarget.Iou(it) to it.amountPaise }
        owed.firstOrNull { it.second == creditPaise }?.let { return listOf(it.first) }
        if (owed.size > 1 && owed.sumOf { it.second } == creditPaise) return owed.map { it.first }
        return null
    }
}
