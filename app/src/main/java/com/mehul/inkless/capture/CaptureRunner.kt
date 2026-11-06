package com.mehul.inkless.capture

import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes captured SMS into the signed-in account's database. Nothing is captured while signed out
 * or in the demo; the catch-up import picks those messages up after the next login.
 */
@Singleton
class CaptureRunner @Inject constructor(
    private val sessions: SessionManager,
    private val databases: DatabaseProvider,
    private val processor: CaptureProcessor,
    private val smsCapture: SmsCapture,
) {
    /** Null when there's no account to capture into. */
    suspend fun currentAccount(): Pair<String, InklessDatabase>? {
        // An SMS can wake the app from cold, before the session has been worked out.
        val session = withTimeoutOrNull(SESSION_WAIT_MS) {
            sessions.session.first { it != Session.Loading }
        } as? Session.SignedIn ?: return null
        val uid = session.user.uid
        val db = withTimeoutOrNull(SESSION_WAIT_MS) {
            databases.current.filterNotNull().first { it.openHelper.databaseName == DatabaseProvider.fileName(uid) }
        } ?: return null
        return uid to db
    }

    /** Saves what it can and returns the new transactions (not ones merged into an existing payment). */
    suspend fun capture(messages: List<IncomingSms>): List<TransactionEntity> {
        val (uid, db) = currentAccount() ?: return emptyList()
        // Each account opts in on its own, even if another account on this phone already allowed SMS.
        if (!smsCapture.isEnabled(uid)) return emptyList()
        return messages.mapNotNull { processor.process(db, uid, it).newTransaction() }
    }

    /** Same for a UPI app notification. Notification access is granted for the whole phone in system settings. */
    suspend fun capture(notification: IncomingNotification): TransactionEntity? {
        val (uid, db) = currentAccount() ?: return null
        return processor.process(db, uid, notification).newTransaction()
    }

    private fun CaptureResult.newTransaction() = transaction.takeIf { outcome == CaptureOutcome.SAVED }

    private companion object {
        const val SESSION_WAIT_MS = 10_000L
    }
}
