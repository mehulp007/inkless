package com.mehul.inkless.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.sync.CloudStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val usernames: UsernameService,
    private val cloud: CloudStore,
) : AuthRepository {

    private val _user = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val user: StateFlow<AuthUser?> = _user.asStateFlow()

    init {
        auth.addAuthStateListener { publish() }
    }

    // The auth state listener doesn't fire for things like emailVerified flipping, so publish by hand too.
    private fun publish() {
        _user.value = auth.currentUser?.toAuthUser()
    }

    override suspend fun refresh() {
        val current = auth.currentUser ?: return
        try {
            current.reload().await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Account was deleted or disabled elsewhere.
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: keep what we have.
        }
        publish()
    }

    override suspend fun isUsernameAvailable(username: String): Boolean = guard(Action.OTHER) {
        usernames.isAvailable(username)
    }

    override suspend fun signUp(username: String, email: String, password: String): String = guard(Action.SIGN_UP) {
        val created = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw AuthException(AuthFailure.UNKNOWN)
        try {
            usernames.claim(created.uid, created.email ?: email.trim(), username)
        } catch (e: Exception) {
            // Don't leave an account behind that has no username.
            runCatching { created.delete().await() }
            throw e
        }
        runCatching { created.sendEmailVerification().await() }
        created.uid
    }

    override suspend fun logIn(identifier: String, password: String) = guard(Action.LOG_IN) {
        val email = resolveEmail(identifier) ?: throw AuthException(AuthFailure.WRONG_CREDENTIALS)
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun signInWithGoogle(idToken: String) = guard(Action.GOOGLE) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun usernameOf(uid: String): String? = guard(Action.OTHER) {
        usernames.usernameOf(uid)
    }

    override suspend fun claimUsername(username: String) = guard(Action.OTHER) {
        val current = requireUser()
        usernames.claim(current.uid, current.email ?: throw AuthException(AuthFailure.UNKNOWN), username)
    }

    override suspend fun sendPasswordReset(identifier: String) = guard(Action.RESET) {
        val email = resolveEmail(identifier) ?: return@guard
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Same outcome as success on purpose.
        }
    }

    override suspend fun sendVerificationEmail() = guard(Action.OTHER) {
        requireUser().sendEmailVerification().await()
        Unit
    }

    override suspend fun linkGoogle(idToken: String) = guard(Action.LINK_GOOGLE) {
        requireUser().linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        publish()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) = guard(Action.REAUTH) {
        val current = requireUser()
        val email = current.email ?: throw AuthException(AuthFailure.UNKNOWN)
        current.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        current.updatePassword(newPassword).await()
        Unit
    }

    override suspend fun deleteAccount(reauth: Reauth) = guard(Action.REAUTH) {
        val current = requireUser()
        val credential = when (reauth) {
            is Reauth.Password -> EmailAuthProvider.getCredential(
                current.email ?: throw AuthException(AuthFailure.UNKNOWN),
                reauth.password,
            )
            is Reauth.Google -> GoogleAuthProvider.getCredential(reauth.idToken, null)
        }
        // Re-authenticating first means the delete below can't fail with "recent login required"
        // after the cloud data is already gone.
        current.reauthenticate(credential).await()
        // The backup first: once the profile is gone nothing would point at it.
        cloud.eraseAll(current.uid)
        usernames.release(current.uid)
        current.delete().await()
        Unit
    }

    override fun logOut() = auth.signOut()

    private suspend fun resolveEmail(identifier: String): String? {
        val trimmed = identifier.trim()
        if (AuthValidation.isEmail(trimmed)) return trimmed
        val username = AuthValidation.normalizeUsername(trimmed)
        if (!AuthValidation.isValidUsername(username)) return null
        return usernames.emailFor(username)
    }

    private fun requireUser(): FirebaseUser =
        auth.currentUser ?: throw AuthException(AuthFailure.RECENT_LOGIN_REQUIRED)

    private enum class Action { SIGN_UP, LOG_IN, GOOGLE, LINK_GOOGLE, RESET, REAUTH, OTHER }

    private suspend fun <T> guard(action: Action, block: suspend () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(failureFor(e, action), e)
    }

    private fun failureFor(e: Exception, action: Action): AuthFailure = when (e) {
        is FirebaseNetworkException -> AuthFailure.NETWORK
        is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_REQUESTS
        is FirebaseAuthWeakPasswordException -> AuthFailure.WEAK_PASSWORD
        is FirebaseAuthRecentLoginRequiredException -> AuthFailure.RECENT_LOGIN_REQUIRED
        is FirebaseAuthUserCollisionException -> when (action) {
            Action.SIGN_UP -> AuthFailure.EMAIL_UNAVAILABLE
            Action.GOOGLE -> AuthFailure.ACCOUNT_EXISTS_WITH_PASSWORD
            Action.LINK_GOOGLE -> AuthFailure.GOOGLE_ALREADY_USED
            else -> AuthFailure.UNKNOWN
        }
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_MISMATCH") AuthFailure.WRONG_GOOGLE_ACCOUNT else AuthFailure.WRONG_CREDENTIALS
        is FirebaseAuthInvalidCredentialsException -> when {
            e.errorCode == "ERROR_INVALID_EMAIL" -> AuthFailure.INVALID_EMAIL
            action == Action.REAUTH -> AuthFailure.WRONG_PASSWORD
            else -> AuthFailure.WRONG_CREDENTIALS
        }
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.ALREADY_EXISTS,
            FirebaseFirestoreException.Code.ABORTED -> AuthFailure.USERNAME_TAKEN
            FirebaseFirestoreException.Code.UNAVAILABLE -> AuthFailure.NETWORK
            else -> AuthFailure.UNKNOWN
        }
        else -> (e.cause as? Exception)?.let { failureFor(it, action) } ?: AuthFailure.UNKNOWN
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    emailVerified = isEmailVerified,
    hasPassword = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
    hasGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
)
