package com.mehul.inkless.capture

import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.SyncState
import kotlin.math.abs

/**
 * One UPI payment usually shows up twice: the bank's SMS and the UPI app's notification. This
 * decides when two captures are the same payment and how to combine them into one transaction.
 */
object Deduplicator {

    /** How far apart the SMS and the notification for one payment can arrive. */
    const val WINDOW_MS = 3 * 60 * 1000L

    /** One bank SMS read twice, e.g. restored from the backup and then imported from this phone's inbox. */
    private const val SAME_SMS_MS = 1_000L

    private val word = Regex("[a-z0-9]{3,}")

    fun isSamePayment(a: TransactionEntity, b: TransactionEntity): Boolean {
        // A reference on both sides settles it either way.
        if (a.upiRef != null && b.upiRef != null) return a.upiRef == b.upiRef
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return false
        if (abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        if (CaptureSource.SMS in a.sources && CaptureSource.SMS in b.sources && abs(a.timestamp - b.timestamp) <= SAME_SMS_MS) {
            return compatiblePayee(a, b)
        }
        // Two SMS (or two notifications) for the same amount minutes apart are two payments: two chais, not one.
        if ((a.sources intersect b.sources).isNotEmpty()) return false
        return compatiblePayee(a, b)
    }

    /** The existing transaction [incoming] duplicates, preferring a reference match. */
    fun findMatch(incoming: TransactionEntity, candidates: List<TransactionEntity>): TransactionEntity? {
        val live = candidates.filter { !it.deleted && it.id != incoming.id }
        return live.firstOrNull { incoming.upiRef != null && it.upiRef == incoming.upiRef }
            ?: live.filter { isSamePayment(incoming, it) }.minByOrNull { abs(it.timestamp - incoming.timestamp) }
    }

    /**
     * Combines two captures of one payment into [existing]'s row. The bank SMS is trusted for the
     * money (amount, reference, account, balance); the app notification usually has the nicer name.
     * A category the user already confirmed is never overwritten.
     */
    fun merge(existing: TransactionEntity, incoming: TransactionEntity, now: Long): TransactionEntity {
        val sms = listOf(existing, incoming).firstOrNull { CaptureSource.SMS in it.sources }
        val notification = listOf(existing, incoming).firstOrNull { CaptureSource.NOTIFICATION in it.sources }
        val other = if (sms === existing) incoming else existing

        val keepExistingCategory = existing.status == TransactionStatus.CONFIRMED ||
            incoming.status == TransactionStatus.NEEDS_REVIEW ||
            (existing.status == TransactionStatus.AUTO && existing.confidence >= incoming.confidence)
        val categorySource = if (keepExistingCategory) existing else incoming

        return existing.copy(
            amountPaise = sms?.amountPaise ?: existing.amountPaise,
            timestamp = sms?.timestamp ?: existing.timestamp,
            upiRef = sms?.upiRef ?: other.upiRef ?: existing.upiRef,
            accountLast4 = sms?.accountLast4 ?: other.accountLast4,
            bankCode = sms?.bankCode ?: other.bankCode,
            balanceAfterPaise = sms?.balanceAfterPaise ?: other.balanceAfterPaise,
            vpa = sms?.vpa ?: other.vpa,
            payeeRaw = notification?.payeeRaw ?: sms?.payeeRaw ?: existing.payeeRaw,
            merchantId = existing.merchantId ?: incoming.merchantId,
            type = existing.type,
            categoryId = categorySource.categoryId,
            confidence = categorySource.confidence,
            status = categorySource.status,
            sources = existing.sources + incoming.sources,
            updatedAt = now,
            syncState = SyncState.DIRTY,
        )
    }

    /**
     * Payees don't contradict each other: matching UPI IDs (allowing for banks that mask the start,
     * like "*1265@upi"), or names sharing a word. When one side has only a UPI ID and the other only
     * a name ("q829154@ybl" vs "Tapri") there's nothing to compare, so that's fine too.
     */
    private fun compatiblePayee(a: TransactionEntity, b: TransactionEntity): Boolean {
        val vpaA = a.vpa?.lowercase()
        val vpaB = b.vpa?.lowercase()
        if (vpaA != null && vpaB != null) return sameVpa(vpaA, vpaB)

        val namesA = words(a.payeeRaw)
        val namesB = words(b.payeeRaw)
        if (namesA.isEmpty() || namesB.isEmpty()) return true
        // Banks cut names short ("TEMPTATION JAIPU"), so a word may be the start of the other.
        return namesA.any { x -> namesB.any { y -> x.startsWith(y) || y.startsWith(x) } }
    }

    private fun sameVpa(a: String, b: String): Boolean = when {
        a.startsWith('*') -> b.endsWith(a.removePrefix("*"))
        b.startsWith('*') -> a.endsWith(b.removePrefix("*"))
        else -> a == b
    }

    private fun words(text: String?): Set<String> =
        text?.lowercase()?.let { t -> word.findAll(t).map { it.value }.toSet() } ?: emptySet()
}
