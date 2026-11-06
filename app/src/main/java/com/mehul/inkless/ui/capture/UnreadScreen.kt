package com.mehul.inkless.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.repo.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class UnreadViewModel @Inject constructor(
    private val repository: LedgerRepository,
) : ViewModel() {

    /** Null while loading. */
    val captures: StateFlow<List<RawCaptureEntity>?> = repository.unparsed()
        .map<List<RawCaptureEntity>, List<RawCaptureEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss(id: String) {
        viewModelScope.launch { repository.dismissUnparsed(id) }
    }
}

/** Bank messages that passed the filter but no parser could read, so formats can be fixed later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnreadScreen(
    onBack: () -> Unit,
    viewModel: UnreadViewModel = hiltViewModel(),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Couldn't read these") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val list = captures
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "Nothing here. Every bank message so far was read fine.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "These look like bank alerts, but Inkless couldn't work out the amount or direction. " +
                        "If one was a real payment, add it as cash from the home screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list.orEmpty(), key = { it.id }) { capture ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text(capture.sender, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                Instant.ofEpochMilli(capture.receivedAt).atZone(ZoneId.systemDefault()).format(WHEN),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(capture.body.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.dismiss(capture.id) }, modifier = Modifier.align(Alignment.End)) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

private val WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a")
