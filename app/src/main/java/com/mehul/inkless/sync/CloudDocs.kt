package com.mehul.inkless.sync

import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType

/**
 * Turns synced rows into Firestore documents and back. Only parsed fields are listed here, so raw
 * SMS and notification text can't reach the cloud: captures have no mapper at all, and the
 * Firestore rules turn away any document with a field that isn't on these lists.
 *
 * The owner and sync state stay local (the owner is the document's path). Reading back tolerates
 * fields it doesn't know, so a newer version of the app can add some without breaking older ones.
 */
object CloudDocs {
    const val UPDATED_AT = "updatedAt"
    const val DELETED = "deleted"

    /** Server time of the last write, filled in by Firestore. Pulls page through this, never device clocks. */
    const val SYNCED_AT = "syncedAt"

    fun category(c: CategoryEntity): Map<String, Any?> = mapOf(
        "name" to c.name,
        "emoji" to c.emoji,
        "colorArgb" to c.colorArgb,
        "monthlyBudgetPaise" to c.monthlyBudgetPaise,
        "sortOrder" to c.sortOrder.toLong(),
        "isSystem" to c.isSystem,
        UPDATED_AT to c.updatedAt,
        DELETED to c.deleted,
    )

    fun category(id: String, data: Map<String, Any?>, ownerUid: String): CategoryEntity? = read(data) {
        CategoryEntity(
            id = id,
            name = string("name"),
            emoji = string("emoji"),
            colorArgb = long("colorArgb"),
            monthlyBudgetPaise = longOrNull("monthlyBudgetPaise"),
            sortOrder = long("sortOrder").toInt(),
            isSystem = bool("isSystem"),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun merchant(m: MerchantEntity): Map<String, Any?> = mapOf(
        "vpa" to m.vpa,
        "displayName" to m.displayName,
        "defaultCategoryId" to m.defaultCategoryId,
        "timesSeen" to m.timesSeen.toLong(),
        UPDATED_AT to m.updatedAt,
        DELETED to m.deleted,
    )

    fun merchant(id: String, data: Map<String, Any?>, ownerUid: String): MerchantEntity? = read(data) {
        MerchantEntity(
            id = id,
            vpa = string("vpa"),
            displayName = string("displayName"),
            defaultCategoryId = stringOrNull("defaultCategoryId"),
            timesSeen = long("timesSeen").toInt(),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun rule(r: RuleEntity): Map<String, Any?> = mapOf(
        "field" to r.field.name,
        "match" to r.match.name,
        "pattern" to r.pattern,
        "categoryId" to r.categoryId,
        "priority" to r.priority.toLong(),
        UPDATED_AT to r.updatedAt,
        DELETED to r.deleted,
    )

    fun rule(id: String, data: Map<String, Any?>, ownerUid: String): RuleEntity? = read(data) {
        RuleEntity(
            id = id,
            field = RuleField.valueOf(string("field")),
            match = RuleMatch.valueOf(string("match")),
            pattern = string("pattern"),
            categoryId = string("categoryId"),
            priority = long("priority").toInt(),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun myAccount(a: MyAccountEntity): Map<String, Any?> = mapOf(
        "match" to a.match.name,
        "value" to a.value,
        "role" to a.role.name,
        "label" to a.label,
        UPDATED_AT to a.updatedAt,
        DELETED to a.deleted,
    )

    fun myAccount(id: String, data: Map<String, Any?>, ownerUid: String): MyAccountEntity? = read(data) {
        MyAccountEntity(
            id = id,
            match = AccountMatch.valueOf(string("match")),
            value = string("value"),
            role = AccountRole.valueOf(string("role")),
            label = stringOrNull("label"),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun budget(b: BudgetEntity): Map<String, Any?> = mapOf(
        "period" to b.period.name,
        "amountPaise" to b.amountPaise,
        "allowanceDay" to b.allowanceDay?.toLong(),
        "dailyFoodCapPaise" to b.dailyFoodCapPaise,
        UPDATED_AT to b.updatedAt,
        DELETED to b.deleted,
    )

    fun budget(id: String, data: Map<String, Any?>, ownerUid: String): BudgetEntity? = read(data) {
        BudgetEntity(
            id = id,
            period = BudgetPeriod.valueOf(string("period")),
            amountPaise = long("amountPaise"),
            allowanceDay = longOrNull("allowanceDay")?.toInt(),
            dailyFoodCapPaise = longOrNull("dailyFoodCapPaise"),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun friend(f: FriendEntity): Map<String, Any?> = mapOf(
        "name" to f.name,
        "vpas" to f.vpas,
        "linkedUid" to f.linkedUid,
        "linkedUsername" to f.linkedUsername,
        UPDATED_AT to f.updatedAt,
        DELETED to f.deleted,
    )

    fun friend(id: String, data: Map<String, Any?>, ownerUid: String): FriendEntity? = read(data) {
        FriendEntity(
            id = id,
            name = string("name"),
            vpas = strings("vpas"),
            linkedUid = stringOrNull("linkedUid"),
            linkedUsername = stringOrNull("linkedUsername"),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    fun iou(i: IouEntity): Map<String, Any?> = mapOf(
        "friendId" to i.friendId,
        "amountPaise" to i.amountPaise,
        "theyOwe" to i.theyOwe,
        "note" to i.note,
        "timestamp" to i.timestamp,
        "settled" to i.settled,
        "settledByTxnId" to i.settledByTxnId,
        UPDATED_AT to i.updatedAt,
        DELETED to i.deleted,
    )

    fun iou(id: String, data: Map<String, Any?>, ownerUid: String): IouEntity? = read(data) {
        IouEntity(
            id = id,
            friendId = string("friendId"),
            amountPaise = long("amountPaise"),
            theyOwe = bool("theyOwe"),
            note = stringOrNull("note"),
            timestamp = long("timestamp"),
            settled = bool("settled"),
            settledByTxnId = stringOrNull("settledByTxnId"),
            ownerUid = ownerUid,
            updatedAt = long(UPDATED_AT),
            deleted = bool(DELETED),
            syncState = SyncState.SYNCED,
        )
    }

    /** Null when the document is damaged or from a newer app version this one can't read. */
    private fun <T> read(data: Map<String, Any?>, build: Fields.() -> T): T? =
        try {
            Fields(data).build()
        } catch (e: IllegalArgumentException) {
            null
        }
}

/**
 * Everything about one payment, kept in one document so it syncs (and wins or loses a conflict)
 * as a whole: the payment and its breakdown [items]. Whatever later belongs to a payment goes in
 * here as another field rather than into a document of its own.
 */
data class TransactionDoc(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity> = emptyList(),
) {

    fun toMap(): Map<String, Any?> = with(transaction) {
        mapOf(
            "amountPaise" to amountPaise,
            "direction" to direction.name,
            "type" to type.name,
            "timestamp" to timestamp,
            "vpa" to vpa,
            "payeeRaw" to payeeRaw,
            "merchantId" to merchantId,
            // Only ever the last four digits, even if a parser slipped up.
            "accountLast4" to accountLast4?.takeLast(4),
            "bankCode" to bankCode,
            "upiRef" to upiRef,
            "balanceAfterPaise" to balanceAfterPaise,
            "categoryId" to categoryId,
            "confidence" to confidence.toDouble(),
            "status" to status.name,
            "sources" to sources.map { it.name }.sorted(),
            "note" to note,
            "tags" to tags,
            "createdAt" to createdAt,
            "items" to items.sortedBy { it.sortOrder }.map { item ->
                mapOf("id" to item.id, "amountPaise" to item.amountPaise, "categoryId" to item.categoryId, "note" to item.note)
            },
            CloudDocs.UPDATED_AT to updatedAt,
            CloudDocs.DELETED to deleted,
        )
    }

    companion object {
        /** Null when the document is damaged or from a newer app version this one can't read. */
        fun fromMap(id: String, data: Map<String, Any?>, ownerUid: String): TransactionDoc? = try {
            val f = Fields(data)
            TransactionDoc(
                TransactionEntity(
                    id = id,
                    amountPaise = f.long("amountPaise"),
                    direction = Direction.valueOf(f.string("direction")),
                    type = TransactionType.valueOf(f.string("type")),
                    timestamp = f.long("timestamp"),
                    vpa = f.stringOrNull("vpa"),
                    payeeRaw = f.stringOrNull("payeeRaw"),
                    merchantId = f.stringOrNull("merchantId"),
                    accountLast4 = f.stringOrNull("accountLast4"),
                    bankCode = f.stringOrNull("bankCode"),
                    upiRef = f.stringOrNull("upiRef"),
                    balanceAfterPaise = f.longOrNull("balanceAfterPaise"),
                    categoryId = f.stringOrNull("categoryId"),
                    confidence = f.double("confidence").toFloat(),
                    status = TransactionStatus.valueOf(f.string("status")),
                    sources = f.strings("sources").map(CaptureSource::valueOf).toSet(),
                    note = f.stringOrNull("note"),
                    tags = f.strings("tags"),
                    createdAt = f.long("createdAt"),
                    updatedAt = f.long(CloudDocs.UPDATED_AT),
                    ownerUid = ownerUid,
                    deleted = f.bool(CloudDocs.DELETED),
                    syncState = SyncState.SYNCED,
                ),
                items = (data["items"] as? List<*>).orEmpty().mapIndexed { index, raw ->
                    val item = Fields(requireNotNull(raw as? Map<*, *>) { "item $index" }.mapKeys { it.key.toString() })
                    TransactionItemEntity(
                        id = item.string("id"),
                        transactionId = id,
                        amountPaise = item.long("amountPaise"),
                        categoryId = item.string("categoryId"),
                        note = item.stringOrNull("note"),
                        sortOrder = index,
                        ownerUid = ownerUid,
                    )
                },
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** A split and its friends' shares, as one document: they sync, and win or lose a conflict, together. */
data class SplitDoc(val split: SplitEntity, val shares: List<SplitShareEntity>) {

    fun toMap(): Map<String, Any?> = with(split) {
        mapOf(
            "transactionId" to transactionId,
            "method" to method.name,
            "sharedSplitId" to sharedSplitId,
            "shares" to shares.sortedBy { it.friendId }.map { share ->
                mapOf(
                    "id" to share.id,
                    "friendId" to share.friendId,
                    "sharePaise" to share.sharePaise,
                    "basisPoints" to share.basisPoints?.toLong(),
                    "settled" to share.settled,
                    "settledByTxnId" to share.settledByTxnId,
                )
            },
            CloudDocs.UPDATED_AT to updatedAt,
            CloudDocs.DELETED to deleted,
        )
    }

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>, ownerUid: String): SplitDoc? = try {
            val f = Fields(data)
            SplitDoc(
                split = SplitEntity(
                    id = id,
                    transactionId = f.string("transactionId"),
                    method = SplitMethod.valueOf(f.string("method")),
                    sharedSplitId = f.stringOrNull("sharedSplitId"),
                    ownerUid = ownerUid,
                    updatedAt = f.long(CloudDocs.UPDATED_AT),
                    deleted = f.bool(CloudDocs.DELETED),
                    syncState = SyncState.SYNCED,
                ),
                shares = (data["shares"] as? List<*>).orEmpty().mapIndexed { index, raw ->
                    val share = Fields(requireNotNull(raw as? Map<*, *>) { "share $index" }.mapKeys { it.key.toString() })
                    SplitShareEntity(
                        id = share.string("id"),
                        splitId = id,
                        friendId = share.string("friendId"),
                        sharePaise = share.long("sharePaise"),
                        basisPoints = share.longOrNull("basisPoints")?.toInt(),
                        settled = share.bool("settled"),
                        settledByTxnId = share.stringOrNull("settledByTxnId"),
                        ownerUid = ownerUid,
                    )
                },
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** Typed reads from a Firestore document, which hands back numbers as Long or Double. */
private class Fields(private val data: Map<String, Any?>) {
    fun string(key: String): String = requireNotNull(stringOrNull(key)) { "$key missing" }
    fun stringOrNull(key: String): String? = data[key] as? String
    fun long(key: String): Long = requireNotNull(longOrNull(key)) { "$key missing" }
    fun longOrNull(key: String): Long? = (data[key] as? Number)?.toLong()
    fun double(key: String): Double = requireNotNull((data[key] as? Number)?.toDouble()) { "$key missing" }
    fun bool(key: String): Boolean = data[key] as? Boolean ?: false
    fun strings(key: String): List<String> = (data[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
