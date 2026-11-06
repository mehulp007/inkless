package com.mehul.inkless.capture

import androidx.room.withTransaction
import com.mehul.inkless.categorize.Categorizer
import com.mehul.inkless.categorize.MerchantMemory
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RawCaptureStatus
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.parsing.BankParsers
import com.mehul.inkless.parsing.SmsReader
import com.mehul.inkless.parsing.SmsResult
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CaptureOutcome { SAVED, UNPARSED, DUPLICATE, SKIPPED }

/** [transaction] is set when one was saved. */
data class CaptureResult(val outcome: CaptureOutcome, val transaction: TransactionEntity? = null)

/** SMS in, transaction out: filter, parse, and save, in one database transaction per message. */
@Singleton
class CaptureProcessor @Inject constructor(
    private val clock: Clock,
) {
    private val reader = SmsReader(BankParsers.all)

    suspend fun process(db: InklessDatabase, ownerUid: String, sms: IncomingSms): CaptureResult {
        val result = reader.read(sms.sender, sms.body)
        // OTPs, promos and personal messages are dropped here and never written anywhere.
        if (result is SmsResult.Rejected) return CaptureResult(CaptureOutcome.SKIPPED)

        val now = clock.millis()
        val transactionId = (result as? SmsResult.Parsed)?.let { UUID.randomUUID().toString() }
        val capture = RawCaptureEntity(
            id = UUID.randomUUID().toString(),
            source = CaptureSource.SMS,
            sender = sms.sender,
            bodyHash = CaptureMapper.hash(sms),
            body = sms.body,
            receivedAt = sms.receivedAt,
            status = if (transactionId != null) RawCaptureStatus.PARSED else RawCaptureStatus.UNPARSED,
            transactionId = transactionId,
        )

        return db.withTransaction {
            if (db.rawCaptureDao().insert(capture) == -1L) return@withTransaction CaptureResult(CaptureOutcome.DUPLICATE)
            if (result !is SmsResult.Parsed || transactionId == null) return@withTransaction CaptureResult(CaptureOutcome.UNPARSED)

            val merchant = MerchantMemory.findOrCreate(db, result.txn.vpa, result.txn.payee, ownerUid, now)
            val decision = Categorizer.decide(
                vpa = result.txn.vpa,
                payee = result.txn.payee,
                rules = db.ruleDao().getAll().map { it.toRule() },
                merchantCategoryId = merchant?.defaultCategoryId,
            )
            val transaction = CaptureMapper.toTransaction(transactionId, result.txn, sms, merchant?.id, ownerUid, now, decision)
            db.transactionDao().insert(transaction)
            CaptureResult(CaptureOutcome.SAVED, transaction)
        }
    }
}
