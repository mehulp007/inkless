package com.mehul.inkless

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.mehul.inkless.lock.AppLock
import com.mehul.inkless.lock.LockGate
import com.mehul.inkless.ui.home.QuickAddRequests
import com.mehul.inkless.ui.navigation.InklessRoot
import com.mehul.inkless.ui.theme.InklessTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// A FragmentActivity (a ComponentActivity that can host Android's own dialogs) because the
// fingerprint/PIN sheet of the app lock needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var quickAdd: QuickAddRequests

    @Inject lateinit var appLock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            InklessTheme {
                LockGate(appLock) { InklessRoot() }
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
