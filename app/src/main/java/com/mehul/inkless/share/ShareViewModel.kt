package com.mehul.inkless.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.breakdown.ShareDrafts
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.OrderText
import com.mehul.inkless.parsing.ParsedOrder
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface ShareUi {
    data object Reading : ShareUi
    data class Problem(val message: String) : ShareUi
    data class Ready(
        val order: ParsedOrder,
        val items: List<DraftItem>,
        /** Likeliest first. */
        val payments: List<TransactionEntity>,
        val selectedId: String?,
        val matchedId: String?,
    ) : ShareUi
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val sessions: SessionManager,
    private val repository: LedgerRepository,
    private val ocr: OrderOcr,
    private val drafts: ShareDrafts,
) : ViewModel() {

    private val _ui = MutableStateFlow<ShareUi>(ShareUi.Reading)
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()

    /** Category names by id, for the items' guessed categories. */
    val categoryNames: StateFlow<Map<String, String>> = repository.categories()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var started = false

    /** What was shared: text, or a screenshot. */
    fun start(text: String?, image: Uri?) {
        if (started) return
        started = true
        viewModelScope.launch { _ui.value = read(text, image) }
    }

    fun select(paymentId: String) = _ui.update { (it as? ShareUi.Ready)?.copy(selectedId = paymentId) ?: it }

    /** Hands the draft to the editor; returns the payment and draft ids to open it with. */
    fun confirm(): Pair<String, String>? {
        val ready = _ui.value as? ShareUi.Ready ?: return null
        val paymentId = ready.selectedId ?: return null
        return paymentId to drafts.put(ready.items)
    }

    private suspend fun read(text: String?, image: Uri?): ShareUi {
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.session.first { it != Session.Loading } }
        if (session !is Session.SignedIn) return ShareUi.Problem("Log in to Inkless first, then share the order again.")

        val content = text ?: image?.let { ocr.read(it) }
            ?: return ShareUi.Problem("Couldn't open what was shared.")
        val order = OrderText.parse(content)
        if (order.items.isEmpty()) {
            return ShareUi.Problem(
                if (image == null) {
                    "No items with prices in that text. Shopping apps often share just a link: " +
                        "share a screenshot of the order details instead."
                } else {
                    "Couldn't find items with prices in that screenshot. Try one of the order details page, " +
                        "with item names and prices showing."
                },
            )
        }

        val payments = ShareMatch.rank(repository.recentSpending(days = LOOK_BACK_DAYS, limit = MAX_PAYMENTS), order)
        if (payments.isEmpty()) return ShareUi.Problem("No payments in the last $LOOK_BACK_DAYS days to break down.")
        val match = ShareMatch.bestMatch(payments, order)
        return ShareUi.Ready(order, ShareMatch.draft(order), payments, selectedId = match?.id, matchedId = match?.id)
    }

    private companion object {
        const val SESSION_WAIT_MS = 10_000L
        const val LOOK_BACK_DAYS = 30L
        const val MAX_PAYMENTS = 30
    }
}
