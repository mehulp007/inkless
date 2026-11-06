package com.mehul.inkless.lock

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    // Ask straight away; the button is for after a cancel.
    LaunchedEffect(Unit) { lock.unlock(activity) }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Inkless is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Use your fingerprint, face or screen lock to open it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { lock.unlock(activity) }) { Text("Unlock") }
            }
        }
    }
}
