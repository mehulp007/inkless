package com.mehul.inkless.session

import com.mehul.inkless.auth.AuthUser

sealed interface Session {
    /** App just started and we don't know yet. */
    data object Loading : Session

    data object SignedOut : Session

    /** "Try demo": generated data, no account. */
    data object Demo : Session

    /** Signed in with Google for the first time and hasn't picked a username. */
    data class NeedsUsername(val user: AuthUser) : Session

    /** [username] is null only when it couldn't be fetched (offline on a fresh install). */
    data class SignedIn(val user: AuthUser, val username: String?) : Session
}
