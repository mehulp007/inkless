package com.mehul.inkless.capture

import androidx.room.withTransaction
import com.mehul.inkless.categorize.Categorizer
import com.mehul.inkless.categorize.MerchantMemory
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.categorize.Decision
import com.mehul.inkless.categorize.DecisionSource
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.PredictionEntity
import com.mehul.inkless.data.db.entity.PredictionSource
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RawCaptureStatus
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.ml.CategoryModel
import com.mehul.inkless.ml.ModelLook
import com.mehul.inkless.parsing.BankParsers
import com.mehul.inkless.parsing.NotificationReader
import com.mehul.inkless.parsing.ParsedTxn
import com.mehul.inkless.parsing.SmsReader
import com.mehul.inkless.parsing.SmsResult
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CaptureOutcome { SAVED, MERGED, UNPARSED, DUPLICATE, SKIPPED }

/** [transaction] is the saved (or merged-into) transaction, when there is one. */
data class CaptureResult(val outcome: CaptureOutcome, val transaction: TransactionEntity? = null)

/**
 * Bank SMS and UPI app notifications in, transactions out: filter, parse, categorise, and either
 * save a new transaction or merge into the one the other source already created.
 */
@Singleton
class CaptureProcessor @Inject constructor(
    private val clock: Clock,
    private val classifier: TransactionClassifier,
    // Left out in tests that don't need the learned model.
    private val model: CategoryModel? = null,
) {
    private val reader = SmsReader(BankParsers.all)

    suspend fun process(db: InklessDatabase, ownerUid: String, sms: IncomingSms): CaptureResult {
        val result = reader.read(sms.sender, sms.body)
        // OTPs, promos and personal messages are dropped here and never written anywhere.
        if (result is SmsResult.Rejected) return CaptureResult(CaptureOutcome.SKIPPED)
        return save(
            db = db,
            ownerUid = ownerUid,
            result = result,
            source = CaptureSource.SMS,
            sender = sms.sender,
            hash = CaptureMapper.hash(sms),
            body = sms.body,
            receivedAt = sms.receivedAt,
            timestamp = CaptureMapper.timeOf(sms),
        )
    }

    suspend fun process(db: InklessDatabase, ownerUid: String, notification: IncomingNotification): CaptureResult {
        val result = NotificationReader.read(notification.packageName, notification.text)
        // Offers, reminders and requests aren't stored either.
        if (result !is SmsResult.Parsed) return CaptureResult(CaptureOutcome.SKIPPED)
        return save(
            db = db,
            ownerUid = ownerUid,
            result = result,
            source = CaptureSource.NOTIFICATION,
            sender = notification.packageName,
            hash = CaptureMapper.hash(notification),
            body = notification.text,
            receivedAt = notification.postedAt,
            timestamp = notification.postedAt,
        )
    }

    private suspend fun save(
        db: InklessDatabase,
        ownerUid: String,
        result: SmsResult,
        source: CaptureSource,
        sender: String,
        hash: String,
        body: String,
        receivedAt: Long,
        timestamp: Long,
    ): CaptureResult {
        val now = clock.millis()
        val parsed = (result as? SmsResult.Parsed)?.txn
        val transactionId = parsed?.let { UUID.randomUUID().toString() }
        val capture = RawCaptureEntity(
            id = UUID.randomUUID().toString(),
            source = source,
            sender = sender,
            bodyHash = hash,
            body = body,
            receivedAt = receivedAt,
            status = if (parsed != null) RawCaptureStatus.PARSED else RawCaptureStatus.UNPARSED,
            transactionId = transactionId,
        )

        return db.withTransaction {
            if (db.rawCaptureDao().insert(capture) == -1L) return@withTransaction CaptureResult(CaptureOutcome.DUPLICATE)
            if (parsed == null || transactionId == null) return@withTransaction CaptureResult(CaptureOutcome.UNPARSED)

            val (incoming, prediction) = build(db, ownerUid, parsed, transactionId, timestamp, source, now)

            val dao = db.transactionDao()
            val candidates = dao.around(timestamp - Deduplicator.WINDOW_MS, timestamp + Deduplicator.WINDOW_MS) +
                (parsed.upiRef?.let { dao.byRef(it) } ?: emptyList())
            val match = Deduplicator.findMatch(incoming, candidates.distinctBy { it.id })
            if (match != null) {
                val merged = Deduplicator.merge(match, incoming, now)
                // Nothing new (the same SMS again): leave the row alone so it isn't uploaded again for nothing.
                if (merged.copy(updatedAt = match.updatedAt, syncState = match.syncState) == match) {
                    db.rawCaptureDao().linkTransaction(capture.id, match.id)
                    return@withTransaction CaptureResult(CaptureOutcome.DUPLICATE, match)
                }
                dao.update(merged)
                db.rawCaptureDao().linkTransaction(capture.id, merged.id)
                return@withTransaction CaptureResult(CaptureOutcome.MERGED, merged)
            }

            dao.insert(incoming)
            prediction?.let { db.insightDao().savePrediction(it) }
            CaptureResult(CaptureOutcome.SAVED, incoming)
        }
    }

    private suspend fun build(
        db: InklessDatabase,
        ownerUid: String,
        parsed: ParsedTxn,
        id: String,
        timestamp: Long,
        source: CaptureSource,
        now: Long,
    ): Pair<TransactionEntity, PredictionEntity?> {
        val merchant = MerchantMemory.findOrCreate(db, parsed.vpa, parsed.payee, ownerUid, now)
        val look = model?.look(db, parsed.vpa, parsed.payee, parsed.amountPaise, timestamp) ?: ModelLook(null, trusted = false)
        val decision = Categorizer.decide(
            vpa = parsed.vpa,
            payee = parsed.payee,
            rules = db.ruleDao().getAll().map { it.toRule() },
            merchantCategoryId = merchant?.defaultCategoryId,
            model = look.best?.takeIf { look.trusted },
        )
        val draft = CaptureMapper.toTransaction(id, parsed, timestamp, source, merchant?.id, ownerUid, now, decision)
        // Own-account transfers, allowance from home and refunds of earlier payments.
        val txn = classifier.classify(db, draft)
        return txn to predictionFor(txn, decision, look, now)
    }

    /** What was guessed about a new spending payment, kept for the accuracy chart. */
    private fun predictionFor(txn: TransactionEntity, decision: Decision?, look: ModelLook, now: Long): PredictionEntity? {
        if (txn.type != TransactionType.EXPENSE || txn.direction != Direction.DEBIT) return null
        val filed = txn.status == TransactionStatus.AUTO && decision != null && decision.categoryId == txn.categoryId
        return PredictionEntity(
            transactionId = txn.id,
            source = if (filed) decision!!.source.toPredictionSource() else PredictionSource.NONE,
            filedCategoryId = txn.categoryId.takeIf { filed },
            modelCategoryId = look.best?.categoryId,
            modelConfidence = look.best?.confidence,
            createdAt = now,
        )
    }
}

fun DecisionSource.toPredictionSource() = when (this) {
    DecisionSource.RULE -> PredictionSource.RULE
    DecisionSource.MERCHANT_MEMORY -> PredictionSource.MERCHANT_MEMORY
    DecisionSource.MODEL -> PredictionSource.MODEL
    DecisionSource.KEYWORD -> PredictionSource.KEYWORD
}
