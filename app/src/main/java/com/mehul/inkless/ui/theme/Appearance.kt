package com.mehul.inkless.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Appearance(val label: String) { LIGHT("Light"), DARK("Dark"), SYSTEM("System") }

/** Light, dark or the phone's own setting. Kept on this phone only; System until changed. */
@Singleton
class AppearancePrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val uiModes = context.getSystemService(UiModeManager::class.java)

    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<Appearance> = _mode.asStateFlow()

    init {
        tellSystem(_mode.value)
    }

    fun set(mode: Appearance) {
        prefs.edit { putString(KEY, mode.name) }
        _mode.value = mode
        tellSystem(mode)
    }

    /**
     * Android 13+ draws its launch screen (before any of the app runs) in the app's own night mode
     * when told it, so the opening matches Light or Dark even when the phone is set the other way.
     * Older versions only follow the phone.
     */
    private fun tellSystem(mode: Appearance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val night = when (mode) {
            Appearance.LIGHT -> UiModeManager.MODE_NIGHT_NO
            Appearance.DARK -> UiModeManager.MODE_NIGHT_YES
            Appearance.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        }
        runCatching { uiModes?.setApplicationNightMode(night) }
    }

    private fun read(): Appearance =
        prefs.getString(KEY, null)?.let { saved -> Appearance.entries.firstOrNull { it.name == saved } } ?: Appearance.SYSTEM

    private companion object {
        const val KEY = "mode"
    }
}

/**
 * The app's theme with the chosen appearance applied. Also tells Android whether the status and
 * navigation bar icons should be dark or light, since the app may be light while the phone is dark.
 */
@Composable
fun ComponentActivity.InklessAppTheme(prefs: AppearancePrefs, content: @Composable () -> Unit) {
    val mode by prefs.mode.collectAsState()
    val dark = when (mode) {
        Appearance.LIGHT -> false
        Appearance.DARK -> true
        Appearance.SYSTEM -> isSystemInDarkTheme()
    }
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    InklessTheme(darkTheme = dark, content = content)
}
