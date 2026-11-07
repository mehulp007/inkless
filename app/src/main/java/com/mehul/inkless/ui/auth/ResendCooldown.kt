package com.mehul.inkless.ui.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** After an email is sent, how many seconds until another may go: 60, 59, … 0. */
class ResendCooldown(private val scope: CoroutineScope, private val seconds: Int = 60) {

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private var ticking: Job? = null

    fun start() {
        ticking?.cancel()
        ticking = scope.launch {
            for (left in seconds downTo 1) {
                _secondsLeft.value = left
                delay(1_000)
            }
            _secondsLeft.value = 0
        }
    }

    companion object {
        /** "0:59" */
        fun format(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
