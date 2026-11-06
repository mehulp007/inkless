package com.mehul.inkless.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The widget's Cash button opens the app with a request for the quick-add sheet; Home picks it up. */
@Singleton
class QuickAddRequests @Inject constructor() {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun handled() {
        _pending.value = false
    }
}
