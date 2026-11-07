package com.mehul.inkless.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate

/** Auto-capture, from Settings or Home's card: the same cards as the first-run step, then past payments. */
@Composable
fun SmsSetupScreen(
    onDone: () -> Unit,
    viewModel: SmsSetupViewModel = hiltViewModel(),
) {
    val granted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val importStarted by viewModel.importStarted.collectAsStateWithLifecycle()
    var months by rememberSaveable { mutableIntStateOf(3) }
    val leave = {
        if (!granted) viewModel.notNow()
        onDone()
    }

    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Auto-capture", onBack = leave)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                PageHeadline(
                    "You pay. It's already logged.",
                    lead = "Inkless reads your bank's payment alerts, so payments from any UPI app find their way here.",
                )
                CaptureSetupCards(viewModel)
                if (granted) {
                    SectionHeader("Add past payments too?")
                    ImportHistorySection(
                        state = importState,
                        started = importStarted,
                        months = months,
                        onMonths = { months = it },
                        today = LocalDate.now(),
                    )
                    when {
                        importState is ImportState.Running -> PrimaryButton("Continue in background", onClick = onDone)
                        importStarted && importState is ImportState.Finished -> PrimaryButton("Done", onClick = onDone)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            PrimaryButton("Import", onClick = { viewModel.importHistory(months) })
                            SecondaryButton("Not now", onClick = onDone)
                        }
                    }
                } else {
                    SecondaryButton("Not now", onClick = leave)
                }
            }
        }
    }
}
