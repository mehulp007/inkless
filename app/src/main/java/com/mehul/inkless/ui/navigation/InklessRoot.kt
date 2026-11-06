package com.mehul.inkless.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.ChooseUsernameScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(sessions: SessionManager) : ViewModel() {
    val session = sessions.session
}

@Composable
fun InklessRoot(viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    when (val current = session) {
        Session.Loading -> Surface(Modifier.fillMaxSize()) {}
        Session.SignedOut -> AuthNavHost()
        is Session.NeedsUsername -> ChooseUsernameScreen(email = current.user.email)
        // Keyed by account so switching accounts starts from a fresh back stack and fresh view models.
        Session.Demo -> key("demo") { InklessNavHost() }
        is Session.SignedIn -> key(current.user.uid) { InklessNavHost() }
    }
}
