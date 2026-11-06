package com.mehul.inkless.session

import android.content.Context
import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.AuthUser
import com.mehul.inkless.auth.GoogleIdTokens
import com.mehul.inkless.auth.Reauth
import com.mehul.inkless.categorize.Recategorizer
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.seed.AccountSeeder
import com.mehul.inkless.demo.Demo
import com.mehul.inkless.demo.DemoSeeder
import com.mehul.inkless.di.ApplicationScope
import com.mehul.inkless.sync.SyncPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides who is using the app (nobody, the demo, or an account) and keeps the matching database
 * open. Screens only read [session]; everything that changes it goes through here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionManager @Inject constructor(
    private val auth: AuthRepository,
    private val googleIdTokens: GoogleIdTokens,
    private val databases: DatabaseProvider,
    private val demoSeeder: DemoSeeder,
    private val accountSeeder: AccountSeeder,
    private val recategorizer: Recategorizer,
    private val prefs: SessionPrefs,
    private val syncPrefs: SyncPrefs,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val demoMode = MutableStateFlow(prefs.demoMode)

    // Sign-up signs the new user in before the username is claimed. Holding the session at
    // SignedOut until it's done keeps the sign-up screen up instead of flashing "choose username".
    private val signingUp = MutableStateFlow(false)

    // Bumped when the username changes without the auth state changing.
    private val profileVersion = MutableStateFlow(0)

    private var openDatabaseKey: String? = null

    val session: StateFlow<Session> =
        combine(auth.user, demoMode, signingUp, profileVersion) { user, demo, busy, _ -> Inputs(user, demo, busy) }
            .flatMapLatest { (user, demo, busy) ->
                when {
                    busy -> flowOf(Session.SignedOut)
                    user != null -> accountSession(user)
                    demo -> flowOf(Session.Demo)
                    else -> flowOf(Session.SignedOut)
                }
            }
            .onEach(::openDatabaseFor)
            .stateIn(scope, SharingStarted.Eagerly, Session.Loading)

    /** Who a new entry belongs to: the signed-in account or the demo. Null when nobody is in. */
    fun currentOwnerUid(): String? = when (val current = session.value) {
        is Session.SignedIn -> current.user.uid
        Session.Demo -> Demo.OWNER_UID
        else -> null
    }

    fun start() {
        scope.launch { auth.refresh() }
    }

    suspend fun refreshUser() = auth.refresh()

    fun enterDemo() = setDemo(true)

    fun exitDemo() = setDemo(false)

    suspend fun signUp(username: String, email: String, password: String) {
        signingUp.value = true
        try {
            val uid = auth.signUp(username, email, password)
            prefs.setUsername(uid, username)
        } finally {
            signingUp.value = false
        }
    }

    suspend fun logIn(identifier: String, password: String) = auth.logIn(identifier, password)

    /** [activity] hosts the Google account picker. */
    suspend fun signInWithGoogle(activity: Context) {
        auth.signInWithGoogle(googleIdTokens.request(activity))
    }

    suspend fun linkGoogle(activity: Context) {
        auth.linkGoogle(googleIdTokens.request(activity))
    }

    suspend fun googleReauth(activity: Context): Reauth = Reauth.Google(googleIdTokens.request(activity))

    suspend fun claimUsername(username: String) {
        auth.claimUsername(username)
        auth.user.value?.let { prefs.setUsername(it.uid, username) }
        profileVersion.update { it + 1 }
    }

    fun logOut() {
        setDemo(false)
        auth.logOut()
        scope.launch { googleIdTokens.clear() }
    }

    suspend fun deleteAccount(reauth: Reauth) {
        val uid = auth.user.value?.uid ?: return
        auth.deleteAccount(reauth)
        prefs.setUsername(uid, null)
        syncPrefs.forget(uid)
        databases.delete(uid)
        googleIdTokens.clear()
    }

    private fun setDemo(enabled: Boolean) {
        prefs.demoMode = enabled
        demoMode.value = enabled
    }

    private fun accountSession(user: AuthUser): Flow<Session> = flow {
        val cached = prefs.username(user.uid)
        if (cached != null) {
            emit(Session.SignedIn(user, cached))
            return@flow
        }
        emit(Session.Loading)
        val username = try {
            auth.usernameOf(user.uid)
        } catch (e: AuthException) {
            // Offline on a fresh install: let them in, we'll find out the username next time.
            emit(Session.SignedIn(user, null))
            return@flow
        }
        if (username == null) {
            emit(Session.NeedsUsername(user))
        } else {
            prefs.setUsername(user.uid, username)
            emit(Session.SignedIn(user, username))
        }
    }

    private fun openDatabaseFor(session: Session) {
        val key = when (session) {
            Session.Loading -> return
            Session.Demo -> Demo.DATABASE_NAME
            is Session.SignedIn -> session.user.uid
            Session.SignedOut, is Session.NeedsUsername -> null
        }
        if (key == openDatabaseKey) return
        openDatabaseKey = key

        if (key == null) {
            databases.close()
            return
        }
        val db = databases.open(key)
        scope.launch {
            if (session is Session.SignedIn) {
                accountSeeder.prepare(db, session.user.uid)
                // Re-sort anything still waiting, e.g. payments imported before categorising existed.
                recategorizer.run(db, session.user.uid)
            } else {
                demoSeeder.prepare(db)
            }
        }
    }

    private data class Inputs(val user: AuthUser?, val demo: Boolean, val signingUp: Boolean)
}
