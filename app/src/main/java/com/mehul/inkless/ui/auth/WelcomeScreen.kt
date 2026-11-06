package com.mehul.inkless.ui.auth

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val sessions: SessionManager,
) : ViewModel() {

    private val runner = FormRunner(viewModelScope)
    val status = runner.status

    fun continueWithGoogle(activity: Context) = runner.run { sessions.signInWithGoogle(activity) }

    fun tryDemo() = sessions.enterDemo()
}

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorResource(R.color.ic_launcher_background)),
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
            }
            Spacer(Modifier.height(20.dp))
            Text("Inkless", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "The ledger that writes itself.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorMessage(status.error)
                PrimaryButton("Create account", onClick = onSignUp, enabled = !status.busy, busy = false)
                OutlinedButton(
                    onClick = onLogIn,
                    enabled = !status.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Log in") }
                GoogleButton(onClick = { viewModel.continueWithGoogle(context) }, enabled = !status.busy)
                TextButton(
                    onClick = viewModel::tryDemo,
                    enabled = !status.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try the demo — no account needed") }
            }
        }
    }
}
