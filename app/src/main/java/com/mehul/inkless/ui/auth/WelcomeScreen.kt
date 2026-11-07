package com.mehul.inkless.ui.auth

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.Wordmark
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
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
    WelcomeContent(
        busy = status.busy,
        error = status.error,
        onSignUp = onSignUp,
        onGoogle = { viewModel.continueWithGoogle(context) },
        onLogIn = onLogIn,
        onDemo = viewModel::tryDemo,
    )
}

@Composable
fun WelcomeContent(
    busy: Boolean,
    error: String?,
    onSignUp: () -> Unit,
    onGoogle: () -> Unit,
    onLogIn: () -> Unit,
    onDemo: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Wordmark()
            Photo()
            Tagline()
            PageHeadline(
                "The ledger that writes itself.",
                lead = "From canteen chai to late-night orders. Your expenses, quietly organised — even offline.",
                style = MaterialTheme.typography.displayLarge,
            )
            ErrorMessage(error)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                PrimaryButton("Create account", onClick = onSignUp, enabled = !busy, icon = R.drawable.icon_arrow_right)
                GoogleButton(onClick = onGoogle, enabled = !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextLink("Log in", onClick = onLogIn, enabled = !busy)
                Spacer(Modifier.weight(1f))
                TextLink("Try the demo →", onClick = onDemo, enabled = !busy)
            }
            Footnote("No account needed for the demo.\nNo ads. No analytics. Just your money, made clear.")
        }
    }
}

/** A quiet hostel afternoon, with a payment already in the ledger over it. */
@Composable
private fun Photo() {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1092f / 744f)
            .clip(MaterialTheme.shapes.large),
    ) {
        Image(
            painterResource(R.drawable.welcome_photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            Modifier.align(Alignment.BottomStart).padding(Spacing.l).fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = Inkless.colors.surface,
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(painterResource(R.drawable.icon_check_check), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text("Chai at the tapri · ₹30", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
                    Text("Already in your ledger.", style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun Tagline() {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Inkless.colors.tint, contentColor = Inkless.colors.primary) {
        Text(
            "LESS TYPING. MORE LIVING.",
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 5.dp),
            style = Inkless.type.overline,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
