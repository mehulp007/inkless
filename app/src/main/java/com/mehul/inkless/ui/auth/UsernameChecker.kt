package com.mehul.inkless.ui.auth

import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.AuthValidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

sealed interface UsernameStatus {
    data object Empty : UsernameStatus
    data class Invalid(val reason: String) : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus
    data object Taken : UsernameStatus

    /** Couldn't ask the server (offline). The claim itself will still catch a clash. */
    data object Unknown : UsernameStatus
}

/** Live "is this username free?" check, waiting for a pause in typing before asking Firestore. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsernameChecker(scope: CoroutineScope, private val auth: AuthRepository) {

    private val input = MutableStateFlow("")

    val status: StateFlow<UsernameStatus> = input
        .transformLatest { name ->
            val problem = if (name.isEmpty()) null else AuthValidation.usernameProblem(name)
            when {
                name.isEmpty() -> emit(UsernameStatus.Empty)
                problem != null -> emit(UsernameStatus.Invalid(problem))
                else -> {
                    emit(UsernameStatus.Checking)
                    delay(DEBOUNCE_MS)
                    val status = try {
                        if (auth.isUsernameAvailable(name)) UsernameStatus.Available else UsernameStatus.Taken
                    } catch (e: AuthException) {
                        UsernameStatus.Unknown
                    }
                    emit(status)
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, UsernameStatus.Empty)

    fun onInput(username: String) {
        input.value = username
    }

    companion object {
        const val DEBOUNCE_MS = 400L

        val UsernameStatus.allowsSubmit: Boolean
            get() = this == UsernameStatus.Available || this == UsernameStatus.Unknown
    }
}
