package com.mehul.inkless.breakdown

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Per account on this phone: above what amount Inkless asks whether a new payment was several things. */
@Singleton
class BreakdownPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("breakdown", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** Null when asking is switched off. */
    fun askAbovePaise(uid: String): Long? =
        prefs.getLong(key(uid), DEFAULT_PAISE).takeIf { it != OFF }

    fun setAskAbovePaise(uid: String, paise: Long?) {
        prefs.edit { putLong(key(uid), paise ?: OFF) }
        _changes.update { it + 1 }
    }

    private fun key(uid: String) = "ask_above_$uid"

    companion object {
        const val DEFAULT_PAISE = 10_000L
        private const val OFF = -1L

        /** What the setting offers, in paise. */
        val CHOICES = listOf(10_000L, 25_000L, 50_000L, 100_000L)
    }
}
