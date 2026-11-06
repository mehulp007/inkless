package com.mehul.inkless.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.ask.Answer
import com.mehul.inkless.ask.AskEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One question and, once worked out, its answer. */
data class Exchange(val question: String, val answer: Answer? = null)

@HiltViewModel
class AskViewModel @Inject constructor(
    private val engine: AskEngine,
) : ViewModel() {

    private val _exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    val exchanges: StateFlow<List<Exchange>> = _exchanges.asStateFlow()

    fun ask(question: String) {
        val q = question.trim().take(200)
        if (q.isEmpty()) return
        val index = _exchanges.value.size
        _exchanges.update { it + Exchange(q) }
        viewModelScope.launch {
            val answer = engine.ask(q)
            _exchanges.update { list -> list.mapIndexed { i, e -> if (i == index) e.copy(answer = answer) else e } }
        }
    }
}
