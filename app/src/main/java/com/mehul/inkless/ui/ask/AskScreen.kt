package com.mehul.inkless.ui.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

internal val SUGGESTIONS = listOf(
    "How much on food this month?",
    "Biggest payment last month",
    "How many times did I order from Swiggy?",
    "Top categories this month",
    "Average per day this week",
    "Kitna kharcha kal?",
)

/** Questions about spending, answered from the phone's own data. */
@Composable
fun AskScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit, viewModel: AskViewModel = hiltViewModel()) {
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
    AskContent(exchanges, onBack, onAsk = viewModel::ask, onOpenTransaction = onOpenTransaction)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskContent(exchanges: List<Exchange>, onBack: () -> Unit, onAsk: (String) -> Unit, onOpenTransaction: (String) -> Unit) {
    val colors = Inkless.colors
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val send = { question: String ->
        if (question.isNotBlank()) {
            onAsk(question)
            text = ""
        }
    }
    LaunchedEffect(exchanges.size) { if (exchanges.isNotEmpty()) list.animateScrollToItem(exchanges.size) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            InklessTopBar("Ask Inkless", onBack = onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        if (exchanges.isEmpty()) {
                            PageHeadline("Ask about your money.", lead = "In plain words, in English or a bit of Hinglish.")
                        }
                        PrivacyNote("Answers come from this phone.", body = "Nothing you ask is sent anywhere.")
                        if (exchanges.isEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                SUGGESTIONS.forEach { Suggestion(it, onClick = { send(it) }) }
                            }
                        }
                    }
                }
                itemsIndexed(exchanges) { _, exchange ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Surface(color = colors.tint, contentColor = colors.ink, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 300.dp)) {
                                Text(exchange.question, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val answer = exchange.answer
                        if (answer == null) {
                            LinearProgressIndicator(Modifier.widthIn(max = 120.dp), color = colors.primary, trackColor = colors.tintStrong)
                        } else {
                            InklessCard {
                                answer.headlinePaise?.let {
                                    Text(Money.format(it), style = Inkless.type.moneyMedium, color = colors.ink)
                                }
                                Text(answer.text, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                                if (answer.understood.isNotEmpty()) {
                                    Overline("Understood as", Modifier.padding(top = Spacing.m, bottom = Spacing.xs))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        answer.understood.forEach { StatusChip(it, tone = ChipTone.Neutral) }
                                    }
                                }
                                if (answer.lines.isNotEmpty()) {
                                    HorizontalDivider(Modifier.padding(top = Spacing.m), color = colors.hairline)
                                    answer.lines.forEach { line ->
                                        LedgerRow(
                                            title = line.title,
                                            amount = Money.format(line.amountPaise),
                                            meta = line.detail,
                                            onClick = line.transactionId?.let { id -> { onOpenTransaction(id) } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            ) {
                Row(Modifier.heightIn(min = 52.dp).padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (text.isEmpty()) Text("How much on Swiggy last week?", style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it.take(200) },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your question" },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(text) }),
                        )
                    }
                    IconButton(onClick = { send(text) }, enabled = text.isNotBlank()) {
                        Icon(
                            painterResource(R.drawable.icon_send),
                            contentDescription = "Ask",
                            tint = if (text.isNotBlank()) colors.primary else colors.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestion(text: String, onClick: () -> Unit) {
    val colors = Inkless.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.primary,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Text(text, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), style = MaterialTheme.typography.labelLarge)
    }
}
