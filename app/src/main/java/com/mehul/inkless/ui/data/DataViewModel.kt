package com.mehul.inkless.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.export.BadBackupFile
import com.mehul.inkless.export.BackupCrypto
import com.mehul.inkless.export.DataExporter
import com.mehul.inkless.export.WrongPassword
import com.mehul.inkless.lock.AppLock
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** What's being written once the user has picked where. */
enum class ExportKind { CSV, JSON, BACKUP }

@HiltViewModel
class DataViewModel @Inject constructor(
    private val databases: DatabaseProvider,
    private val exporter: DataExporter,
    private val sessions: SessionManager,
    val appLock: AppLock,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A picked backup file that needs its password. */
    private val _needsPassword = MutableStateFlow<Uri?>(null)
    val needsPassword: StateFlow<Uri?> = _needsPassword.asStateFlow()

    val isDemo: Boolean get() = sessions.session.value is Session.Demo

    private var backupPassword: CharArray? = null

    fun lockAvailable() = appLock.available()

    /** Remembered until the file is written; the backup needs it, nothing else keeps it. */
    fun setBackupPassword(password: String) {
        backupPassword = password.toCharArray()
    }

    fun export(kind: ExportKind, uri: Uri) = run {
        val db = databases.current.value ?: return@run
        val bytes = when (kind) {
            ExportKind.CSV -> exporter.csv(db).toByteArray()
            ExportKind.JSON -> exporter.json(db).toByteArray()
            ExportKind.BACKUP -> {
                val password = backupPassword ?: return@run
                withContext(Dispatchers.Default) { exporter.encrypt(exporter.json(db), password) }.also {
                    password.fill(' ')
                    backupPassword = null
                }
            }
        }
        exporter.writeTo(uri, bytes)
        _message.value = when (kind) {
            ExportKind.CSV -> "Payments saved as a spreadsheet"
            ExportKind.JSON -> "Everything saved as JSON"
            ExportKind.BACKUP -> "Backup file saved. Keep the password somewhere safe: without it the file can't be opened."
        }
    }

    fun restore(uri: Uri) = run {
        val bytes = exporter.readFrom(uri)
        if (BackupCrypto.isEncrypted(bytes)) _needsPassword.value = uri else restoreBytes(bytes, null)
    }

    fun restoreWithPassword(password: String) = run {
        val uri = _needsPassword.value ?: return@run
        _needsPassword.value = null
        restoreBytes(exporter.readFrom(uri), password.toCharArray())
    }

    fun passwordDismissed() {
        _needsPassword.value = null
    }

    private suspend fun restoreBytes(bytes: ByteArray, password: CharArray?) {
        val db = databases.current.value ?: return
        val owner = sessions.currentOwnerUid() ?: return
        val result = withContext(Dispatchers.Default) { exporter.restore(db, bytes, password, owner) }
        password?.fill(' ')
        _message.value = when {
            result.total == 0 -> "Nothing new in that backup: everything in it is already here."
            else -> "Restored ${result.payments} ${if (result.payments == 1) "payment" else "payments"} and ${result.total - result.payments} other things."
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: WrongPassword) {
                _message.value = "Wrong password, or the file is damaged."
            } catch (e: BadBackupFile) {
                _message.value = e.message
            } catch (e: IOException) {
                _message.value = "Couldn't read or write that file."
            } catch (e: IllegalStateException) {
                _message.value = "Couldn't read or write that file."
            } finally {
                _busy.value = false
            }
        }
    }
}
