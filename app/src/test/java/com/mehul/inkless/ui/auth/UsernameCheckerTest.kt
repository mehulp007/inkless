package com.mehul.inkless.ui.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthFailure
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.AuthUser
import com.mehul.inkless.auth.Reauth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameCheckerTest {

    private val auth = FakeAuth(taken = setOf("mehul"))

    @Test
    fun `free name goes through checking to available`() = runTest {
        val checker = UsernameChecker(backgroundScope, auth)
        checker.status.test {
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Empty)
            checker.onInput("chai_wala")
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Checking)
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Available)
        }
    }

    @Test
    fun `taken name is reported`() = runTest {
        val checker = UsernameChecker(backgroundScope, auth)
        checker.status.test {
            skipItems(1)
            checker.onInput("mehul")
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Checking)
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Taken)
        }
    }

    @Test
    fun `invalid names never hit the server`() = runTest {
        val checker = UsernameChecker(backgroundScope, auth)
        checker.status.test {
            skipItems(1)
            checker.onInput("ab")
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Invalid("At least 3 characters"))
        }
        assertThat(auth.lookups).isEmpty()
    }

    @Test
    fun `only asks once typing pauses`() = runTest {
        val checker = UsernameChecker(backgroundScope, auth)
        checker.status.test {
            skipItems(1)
            for (name in listOf("cha", "chai", "chai_", "chai_w")) {
                checker.onInput(name)
                advanceTimeBy(UsernameChecker.DEBOUNCE_MS / 2)
            }
            // The intermediate names emit Checking once (repeats are swallowed), then the last one resolves.
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Checking)
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Available)
        }
        assertThat(auth.lookups).containsExactly("chai_w")
    }

    @Test
    fun `offline check is unknown rather than an error`() = runTest {
        auth.offline = true
        val checker = UsernameChecker(backgroundScope, auth)
        checker.status.test {
            skipItems(1)
            checker.onInput("chai_wala")
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Checking)
            assertThat(awaitItem()).isEqualTo(UsernameStatus.Unknown)
        }
    }

    private class FakeAuth(private val taken: Set<String>) : AuthRepository {
        val lookups = mutableListOf<String>()
        var offline = false

        override suspend fun isUsernameAvailable(username: String): Boolean {
            if (offline) throw AuthException(AuthFailure.NETWORK)
            lookups += username
            return username !in taken
        }

        override val user: StateFlow<AuthUser?> = MutableStateFlow(null)
        override suspend fun refresh() = Unit
        override suspend fun signUp(username: String, email: String, password: String) = error("unused")
        override suspend fun logIn(identifier: String, password: String) = error("unused")
        override suspend fun signInWithGoogle(idToken: String) = error("unused")
        override suspend fun usernameOf(uid: String) = error("unused")
        override suspend fun claimUsername(username: String) = error("unused")
        override suspend fun sendPasswordReset(identifier: String) = error("unused")
        override suspend fun sendVerificationEmail() = error("unused")
        override suspend fun linkGoogle(idToken: String) = error("unused")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = error("unused")
        override suspend fun deleteAccount(reauth: Reauth) = error("unused")
        override fun logOut() = error("unused")
    }
}
