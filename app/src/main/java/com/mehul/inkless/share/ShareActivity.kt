package com.mehul.inkless.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.MainActivity
import com.mehul.inkless.lock.AppLock
import com.mehul.inkless.lock.LockGate
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.InklessTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                LockGate(appLock) { ShareScreen(viewModel, onClose = ::finish, onContinue = ::openEditor) }
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

    private fun openEditor() {
        val (paymentId, draftId) = viewModel.confirm() ?: return
        startActivity(
            Intent(Intent.ACTION_VIEW, "inkless://breakdown/$paymentId?draft=$draftId".toUri(), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareScreen(viewModel: ShareViewModel, onClose: () -> Unit, onContinue: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Break down a payment") }) }) { padding ->
        when (val state = ui) {
            ShareUi.Reading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reading your order on this phone…")
                }
            }
            is ShareUi.Problem -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onClose) { Text("Close") }
            }
            is ShareUi.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${state.items.size} items found", style = MaterialTheme.typography.titleMedium)
                state.order.items.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(item.amountPaise), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.order.totalPaise?.let {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total paid", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(Money.format(it), fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Which payment was this?", style = MaterialTheme.typography.titleMedium)
                state.payments.forEach { payment ->
                    PaymentChoice(
                        title = payment.payeeRaw ?: payment.vpa ?: "Payment",
                        amountPaise = payment.amountPaise,
                        timestamp = payment.timestamp,
                        sameAmount = payment.id == state.matchedId,
                        selected = payment.id == state.selectedId,
                        onSelect = { viewModel.select(payment.id) },
                    )
                }
                Text(
                    "Next you check the items and categories. Nothing is saved until you do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onContinue, enabled = state.selectedId != null, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Check the breakdown")
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PaymentChoice(title: String, amountPaise: Long, timestamp: Long, sameAmount: Boolean, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(WHEN_FORMAT) +
                    if (sameAmount) "  ·  same amount as the order" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (sameAmount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("d MMM, h:mm a")
