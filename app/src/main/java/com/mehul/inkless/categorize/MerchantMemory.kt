package com.mehul.inkless.categorize

import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.MerchantEntity
import java.util.UUID

/** "Ask once, remember forever": one merchant per payee, and the category the user gave it. */
object MerchantMemory {

    /** The merchant these details belong to, created on first sight. Null when there's nothing to key on. */
    suspend fun findOrCreate(
        db: InklessDatabase,
        vpa: String?,
        payee: String?,
        ownerUid: String,
        now: Long,
    ): MerchantEntity? {
        val key = MerchantKeys.of(vpa, payee) ?: return null
        val dao = db.merchantDao()
        dao.findByVpa(key)?.let {
            dao.markSeen(it.id, now)
            return it
        }
        val merchant = MerchantEntity(
            // Same id for the same payee on every device, so sync can line them up later.
            id = UUID.nameUUIDFromBytes("$ownerUid:$key".toByteArray()).toString(),
            vpa = key,
            displayName = payee ?: vpa ?: key,
            timesSeen = 1,
            ownerUid = ownerUid,
            updatedAt = now,
        )
        dao.insert(merchant)
        return merchant
    }

    /** Remembers [categoryId] for the merchant and files its other waiting payments. Returns how many were filed. */
    suspend fun learn(db: InklessDatabase, merchantId: String, categoryId: String, now: Long): Int {
        db.merchantDao().setDefaultCategory(merchantId, categoryId, now)
        return db.transactionDao().fileWaitingForMerchant(merchantId, categoryId, now)
    }
}
