package com.mehul.inkless.share

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.mehul.inkless.MainActivity
import com.mehul.inkless.lock.AppLock
import com.mehul.inkless.lock.LockGate
import com.mehul.inkless.ui.theme.InklessTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * "Share to Inkless": an order's text or a screenshot of it, shared from any app. Reads the items
 * on the phone, asks which payment it was, then opens the Break down editor filled in. Nothing is
 * saved here; the user checks the breakdown and saves it in the editor.
 */
@AndroidEntryPoint
class ShareActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLock

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { intent.type?.startsWith("text/") == true }
        val image = sharedImage()
        if (image == null && text == null) android.util.Log.w("ShareActivity", "Nothing usable shared: type=${intent.type}")
        viewModel.start(text, image)
        setContent {
            InklessTheme {
                LockGate(appLock) { ShareScreen(viewModel, sharedFrom = sharedFrom(), onClose = ::finish, onContinue = ::openEditor) }
            }
        }
    }

    private fun sharedImage(): Uri? {
        if (intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    /** The app the order came from ("Blinkit"), when Android says and its name can be looked up. */
    private fun sharedFrom(): String? {
        val pkg = referrer?.host?.takeIf { it != packageName } ?: return null
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun openEditor() {
        val (paymentId, draftId) = viewModel.confirm() ?: return
        startActivity(
            Intent(Intent.ACTION_VIEW, "inkless://breakdown/$paymentId?draft=$draftId".toUri(), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}
