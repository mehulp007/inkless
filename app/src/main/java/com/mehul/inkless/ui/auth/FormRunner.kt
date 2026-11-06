package com.mehul.inkless.ui.auth

import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormStatus(
    val busy: Boolean = false,
    val error: String? = null,
    /** Set after something worked that doesn't navigate away by itself (e.g. "reset link sent"). */
    val done: Boolean = false,
)

/** The busy/error bookkeeping every auth form needs, so each view model only says what to call. */
class FormRunner(private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(FormStatus())
    val status: StateFlow<FormStatus> = _status.asStateFlow()

    fun run(block: suspend () -> Unit) {
        if (_status.value.busy) return
        _status.value = FormStatus(busy = true)
        scope.launch {
            _status.value = try {
                block()
                FormStatus(done = true)
            } catch (e: AuthException) {
                // Backing out of the Google picker isn't an error worth showing.
                FormStatus(error = e.failure.message.takeUnless { e.failure == AuthFailure.CANCELLED })
            }
        }
    }

    fun clearError() = _status.update { it.copy(error = null) }

    fun reset() {
        _status.value = FormStatus()
    }
}
