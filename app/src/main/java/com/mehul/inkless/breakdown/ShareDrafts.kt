package com.mehul.inkless.breakdown

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Breakdowns read from a shared order, waiting for the user to check them in the editor. Kept in
 * memory only: nothing from a shared order is saved until the user saves the breakdown.
 */
@Singleton
class ShareDrafts @Inject constructor() {
    private val drafts = mutableMapOf<String, List<DraftItem>>()

    @Synchronized
    fun put(items: List<DraftItem>): String = UUID.randomUUID().toString().also { drafts[it] = items }

    @Synchronized
    fun take(id: String): List<DraftItem>? = drafts.remove(id)
}
