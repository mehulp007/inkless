package com.mehul.inkless.lock

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ui.components.BrandTile
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/**
 * Shows [content] only while the app is unlocked. With the lock on, the app's screen is also left
 * out of the recent-apps preview (Android 13+), so spending doesn't show in the app switcher.
 */
@Composable
fun LockGate(lock: AppLock, content: @Composable () -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    val enabled by lock.enabled.collectAsStateWithLifecycle()
    val locked by lock.locked.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        lock.returned()
        onStopOrDispose { lock.left() }
    }
    LaunchedEffect(enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(!enabled)
    }

    if (!locked) {
        content()
        return
    }
    var problem by remember { mutableStateOf<String?>(null) }
    val unlock = {
        problem = null
        lock.unlock(activity) { problem = it.toString() }
    }
    // Ask straight away; the button is for after a cancel.
    LaunchedEffect(Unit) { unlock() }
    LockedScreen(problem = problem, onUnlock = unlock)
}

/** What's on screen while the app is locked: the mark, one line, and the button to try again. */
@Composable
fun LockedScreen(problem: String?, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Inkless.colors.tint) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        ) {
            BrandTile(size = 56.dp)
            Text(
                "Your ledger is locked.",
                style = MaterialTheme.typography.displaySmall,
                color = Inkless.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                "Uses your phone's fingerprint, face or screen lock. Inkless never sees them.",
                style = MaterialTheme.typography.bodyMedium,
                color = Inkless.colors.muted,
                textAlign = TextAlign.Center,
            )
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.danger, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.padding(top = Spacing.s))
            PrimaryButton("Unlock", onClick = onUnlock, icon = R.drawable.icon_fingerprint)
        }
    }
}
