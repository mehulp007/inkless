package com.mehul.inkless.ui.ask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ask.AnswerLine
import com.mehul.inkless.ui.format.Money

private val SUGGESTIONS = listOf(
    "How much on food this month?",
    "Biggest payment last month",
    "How many times did I order from Swiggy?",
    "Top categories this month",
    "Average per day this week",
    "Kitna kharcha kal?",
)

/** Questions about spending, answered from the phone's own data. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit, viewModel: AskViewModel = hiltViewModel()) {
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    fun send(question: String) {
        viewModel.ask(question)
        text = ""
    }
    LaunchedEffect(exchanges.size) { if (exchanges.isNotEmpty()) list.animateScrollToItem(exchanges.lastIndex) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask Inkless") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (exchanges.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Ask about your spending in plain words. Answers come from this phone; nothing is sent anywhere.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SUGGESTIONS.forEach { AssistChip(onClick = { send(it) }, label = { Text(it) }) }
                            }
                        }
                    }
                }
                itemsIndexed(exchanges) { _, exchange ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Bubble(exchange.question, mine = true)
                        }
                        val answer = exchange.answer
                        if (answer == null) {
                            LinearProgressIndicator(Modifier.widthIn(max = 120.dp))
                        } else {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(answer.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    answer.lines.forEach { AnswerRow(it, onOpenTransaction) }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("How much on Swiggy last week?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(text) }),
                )
                TextButton(onClick = { send(text) }, enabled = text.isNotBlank()) { Text("Ask") }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Surface(
        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun AnswerRow(line: AnswerLine, onOpenTransaction: (String) -> Unit) {
    val open = line.transactionId
    Row(
        Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable { onOpenTransaction(open) } else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.bodyMedium)
            line.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(Money.format(line.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
