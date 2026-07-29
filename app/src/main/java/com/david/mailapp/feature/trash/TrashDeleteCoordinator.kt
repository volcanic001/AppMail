package com.david.mailapp.feature.trash

/**
 * Internal seam for the permanent-delete interaction.
 *
 * It intentionally preserves the current behavior during Phase 0: a request
 * deletes immediately and offers undo. Contract C9 documents the target
 * behavior and remains ignored until Phase 2.5 changes this coordinator and
 * its UI representation to require confirmation and remove undo.
 */
internal class TrashDeleteCoordinator(
    private val deletePermanently: (String) -> Unit,
    private val restoreToInbox: (String) -> Unit
) {
    var pendingEmailId: String? = null
        private set

    val offersUndo: Boolean = true

    fun requestDelete(emailId: String) {
        deletePermanently(emailId)
    }

    fun confirmDelete() {
        pendingEmailId?.let(deletePermanently)
        pendingEmailId = null
    }

    fun undo(emailId: String) {
        restoreToInbox(emailId)
    }
}
