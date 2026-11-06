package com.mehul.inkless.categorize

import androidx.room.withTransaction
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.capture.toPredictionSource
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.ml.CategoryModel
import java.time.Clock
import javax.inject.Inject

/**
 * Takes another look at every payment still waiting for a category, after something that could
 * file it changed: the account opened, a rule was added, or a merchant was taught a category.
 * Payments the user already categorised are never touched.
 */
class Recategorizer @Inject constructor(
    private val clock: Clock,
    // Left out in tests that don't need the learned model.
    private val model: CategoryModel? = null,
) {
    /** Returns how many payments got filed. */
    suspend fun run(db: InklessDatabase, ownerUid: String): Int {
        val now = clock.millis()
        return db.withTransaction {
            val transactions = db.transactionDao()

            // Older captures only had a merchant when the SMS carried a UPI ID; give the rest one by name.
            for (payment in transactions.unlinked()) {
                MerchantMemory.findOrCreate(db, payment.vpa, payment.payeeRaw, ownerUid, now)
                    ?.let { transactions.linkMerchant(payment.id, it.id, now) }
            }

            val rules = db.ruleDao().getAll().map { it.toRule() }
            var filed = 0
            for (candidate in transactions.needsReview()) {
                // The model learned from spending, so refunds are left to rules and memory.
                val look = model?.takeIf { candidate.type == TransactionType.EXPENSE }
                    ?.look(db, candidate.vpa, candidate.payeeRaw, candidate.amountPaise, candidate.timestamp)
                val decision = Categorizer.decide(
                    candidate.vpa,
                    candidate.payeeRaw,
                    rules,
                    candidate.merchantCategoryId,
                    look?.best?.takeIf { look.trusted },
                )
                    ?.takeIf { it.confidence >= Categorizer.AUTO_FILE_THRESHOLD }
                    ?: continue
                transactions.file(candidate.id, decision.categoryId, TransactionStatus.AUTO, decision.confidence, now)
                // So the accuracy chart knows who filed it, and the model doesn't learn from its own guess.
                db.insightDao().markFiled(candidate.id, decision.source.toPredictionSource(), decision.categoryId)
                filed++
            }
            filed
        }
    }
}
