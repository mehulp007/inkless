package com.mehul.inkless.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional app lock: when it's on, Inkless asks for a fingerprint, face or the phone's own
 * screen lock whenever it's opened, or comes back after more than [GRACE_MS] away. It's a setting
 * of this phone, not of an account, since it's about who's holding the phone.
 *
 * BiometricPrompt is Android's own unlock sheet: the app never sees a fingerprint or the PIN, only
 * "succeeded" or "failed".
 */
@Singleton
class AppLock @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // Starts locked: a fresh start of the app always asks.
    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt: Long? = null

    /** Whether the phone has something to unlock with: a fingerprint, a face, or at least a PIN or pattern. */
    fun available(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        _enabled.value = on
        if (!on) _locked.value = false
    }

    /** The app went to the background. */
    fun left() {
        leftAt = clock.millis()
    }

    /** The app came back: lock again if it was away long enough. */
    fun returned() {
        val away = leftAt?.let { clock.millis() - it } ?: return
        if (_enabled.value && away > GRACE_MS) _locked.value = true
    }

    /** Shows the unlock sheet; [onResult] gets whether the person got in. */
    fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                // A failed fingerprint keeps the sheet open for another try; only errors (cancelled,
                // too many attempts) end it.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    fun unlock(activity: FragmentActivity) {
        prompt(activity, "Unlock Inkless") { ok -> if (ok) _locked.value = false }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val GRACE_MS = 30_000L
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
