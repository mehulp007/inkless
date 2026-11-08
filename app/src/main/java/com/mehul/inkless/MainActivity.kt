package com.mehul.inkless

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.mehul.inkless.lock.AppLock
import com.mehul.inkless.lock.LockGate
import com.mehul.inkless.ui.home.QuickAddRequests
import com.mehul.inkless.ui.launch.LaunchIntro
import com.mehul.inkless.ui.launch.LaunchIntroGate
import com.mehul.inkless.ui.navigation.InklessRoot
import com.mehul.inkless.ui.theme.AppearancePrefs
import com.mehul.inkless.ui.theme.InklessAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// A FragmentActivity (a ComponentActivity that can host Android's own dialogs) because the
// fingerprint/PIN sheet of the app lock needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var quickAdd: QuickAddRequests

    @Inject lateinit var appLock: AppLock

    @Inject lateinit var appearance: AppearancePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        // The opening plays when the app starts, not when it comes back from the background or turns.
        val playIntro = savedInstanceState == null && LaunchIntroGate.claim()
        if (playIntro && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android's launch screen shows the same picture the intro starts from, so drop it
            // straight away instead of letting it fade over the intro.
            splashScreen.setOnExitAnimationListener { view -> view.remove() }
        }
        setContent {
            InklessAppTheme(appearance) {
                var intro by rememberSaveable { mutableStateOf(playIntro) }
                Box {
                    // The app loads underneath while the intro plays; the unlock prompt waits for it.
                    LockGate(appLock, askNow = !intro) { InklessRoot() }
                    if (intro) LaunchIntro(onFinished = { intro = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_ADD_CASH) quickAdd.request()
    }

    companion object {
        /** The home-screen widget's Cash button. */
        const val ACTION_QUICK_ADD_CASH = "com.mehul.inkless.QUICK_ADD_CASH"
    }
}
