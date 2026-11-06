package com.mehul.inkless.session

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The few bits of session state that have to survive a restart before Firebase answers. */
@Singleton
class SessionPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit { putBoolean(KEY_DEMO, value) }

    // Cached so the app opens straight into the account offline instead of waiting on Firestore.
    fun username(uid: String): String? = prefs.getString(usernameKey(uid), null)

    fun setUsername(uid: String, username: String?) = prefs.edit {
        if (username == null) remove(usernameKey(uid)) else putString(usernameKey(uid), username)
    }

    private fun usernameKey(uid: String) = "username_$uid"

    private companion object {
        const val KEY_DEMO = "demo_mode"
    }
}
