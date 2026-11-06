package com.mehul.inkless.capture.sms

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mehul.inkless.di.ApplicationScope
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/** SMS capture settings per account, and the background imports. */
@Singleton
class SmsCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionManager,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
    private val work = WorkManager.getInstance(context)

    // Bumped so screens re-check after the user finishes or skips setup.
    private val changes = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = changes.asStateFlow()

    /** After any login, quietly pick up SMS that arrived while this account wasn't capturing. */
    fun start() {
        sessions.session
            .map { (it as? Session.SignedIn)?.user?.uid }
            .distinctUntilChanged()
            .onEach { uid -> if (uid != null && isEnabled(uid)) enqueue(uid, InboxImportWorker.MODE_CATCH_UP) }
            .launchIn(scope)
    }

    fun permissionGranted(): Boolean = SmsPermissions.granted(context)

    /** The user went through setup for this account and allowed SMS access. */
    fun isEnabled(uid: String): Boolean = prefs.getBoolean(enabledKey(uid), false) && permissionGranted()

    fun setupSeen(uid: String): Boolean = prefs.getBoolean(seenKey(uid), false)

    fun markSetupSeen(uid: String) {
        prefs.edit { putBoolean(seenKey(uid), true) }
        changes.value++
    }

    fun enable(uid: String) {
        prefs.edit {
            putBoolean(enabledKey(uid), true)
            // Remembered once, so turning permission off and on again doesn't reset it.
            if (!prefs.contains(enabledAtKey(uid))) putLong(enabledAtKey(uid), System.currentTimeMillis())
            putBoolean(seenKey(uid), true)
        }
        changes.value++
    }

    fun importHistory(uid: String, months: Int) = enqueue(uid, InboxImportWorker.MODE_HISTORY, months)

    fun historyImport(): Flow<WorkInfo?> =
        work.getWorkInfosForUniqueWorkFlow(HISTORY_WORK).map { it.lastOrNull() }

    private fun enqueue(uid: String, mode: String, months: Int = 3) {
        val request = OneTimeWorkRequestBuilder<InboxImportWorker>()
            .setInputData(InboxImportWorker.input(uid, mode, months, enabledAt = enabledAt(uid)))
            .build()
        val name = if (mode == InboxImportWorker.MODE_HISTORY) HISTORY_WORK else CATCH_UP_WORK
        val policy = if (mode == InboxImportWorker.MODE_HISTORY) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        work.enqueueUniqueWork(name, policy, request)
    }

    // Accounts that switched capture on before this was recorded start counting from now.
    private fun enabledAt(uid: String): Long {
        if (!prefs.contains(enabledAtKey(uid))) prefs.edit { putLong(enabledAtKey(uid), System.currentTimeMillis()) }
        return prefs.getLong(enabledAtKey(uid), System.currentTimeMillis())
    }

    private fun enabledKey(uid: String) = "sms_enabled_$uid"
    private fun seenKey(uid: String) = "sms_setup_seen_$uid"
    private fun enabledAtKey(uid: String) = "sms_enabled_at_$uid"

    private companion object {
        const val HISTORY_WORK = "sms-history-import"
        const val CATCH_UP_WORK = "sms-catch-up"
    }
}
