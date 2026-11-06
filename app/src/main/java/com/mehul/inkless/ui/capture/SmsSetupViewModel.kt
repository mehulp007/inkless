package com.mehul.inkless.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.mehul.inkless.capture.sms.InboxImportWorker
import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ImportState {
    data object NotStarted : ImportState
    data class Running(val done: Int, val total: Int) : ImportState
    data class Finished(val saved: Int, val unparsed: Int, val scanned: Int) : ImportState
    data object Failed : ImportState
}

@HiltViewModel
class SmsSetupViewModel @Inject constructor(
    private val smsCapture: SmsCapture,
    sessions: SessionManager,
) : ViewModel() {

    private val uid = (sessions.session.value as? Session.SignedIn)?.user?.uid

    private val _permissionGranted = MutableStateFlow(smsCapture.permissionGranted())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _importStarted = MutableStateFlow(false)
    val importStarted: StateFlow<Boolean> = _importStarted

    val importState: StateFlow<ImportState> = smsCapture.historyImport()
        .map { info -> info.toImportState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportState.NotStarted)

    /** Called with the result of the permission dialog, and again on resume in case it changed in Settings. */
    fun refreshPermission(granted: Boolean = smsCapture.permissionGranted()) {
        _permissionGranted.value = granted
        if (granted && uid != null) smsCapture.enable(uid)
    }

    fun importHistory(months: Int) {
        uid ?: return
        _importStarted.value = true
        smsCapture.importHistory(uid, months)
    }

    fun notNow() {
        uid?.let(smsCapture::markSetupSeen)
    }

    private fun WorkInfo?.toImportState(): ImportState = when (this?.state) {
        null, WorkInfo.State.CANCELLED -> ImportState.NotStarted
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ImportState.Running(0, 0)
        WorkInfo.State.RUNNING -> ImportState.Running(
            done = progress.getInt(InboxImportWorker.KEY_DONE, 0),
            total = progress.getInt(InboxImportWorker.KEY_TOTAL, 0),
        )
        WorkInfo.State.SUCCEEDED -> ImportState.Finished(
            saved = outputData.getInt(InboxImportWorker.KEY_SAVED, 0),
            unparsed = outputData.getInt(InboxImportWorker.KEY_UNPARSED, 0),
            scanned = outputData.getInt(InboxImportWorker.KEY_TOTAL, 0),
        )
        WorkInfo.State.FAILED -> ImportState.Failed
    }
}
