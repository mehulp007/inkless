package com.mehul.inkless.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
    val hasPassword: Boolean,
    val hasGoogle: Boolean,
)

sealed interface Reauth {
    data class Password(val password: String) : Reauth
    data class Google(val idToken: String) : Reauth
}

/** All calls throw [AuthException] on failure. */
interface AuthRepository {

    /** The signed-in user, or null. Survives app restarts. */
    val user: StateFlow<AuthUser?>

    /** Picks up changes made outside the app, like clicking the verification link. */
    suspend fun refresh()

    suspend fun isUsernameAvailable(username: String): Boolean

    /** Returns the new user's uid. Creates the account, claims the username and sends a verification email. */
    suspend fun signUp(username: String, email: String, password: String): String

    /** [identifier] is a username or an email. */
    suspend fun logIn(identifier: String, password: String)

    suspend fun signInWithGoogle(idToken: String)

    /** The username stored on the account, or null if it never picked one (first Google sign-in). */
    suspend fun usernameOf(uid: String): String?

    suspend fun claimUsername(username: String)

    /** Succeeds quietly when there's no such account, so the screen can't be used to probe for one. */
    suspend fun sendPasswordReset(identifier: String)

    suspend fun sendVerificationEmail()

    suspend fun linkGoogle(idToken: String)

    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun deleteAccount(reauth: Reauth)

    fun logOut()
}
