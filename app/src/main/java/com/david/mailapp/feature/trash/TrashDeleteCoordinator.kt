package com.david.mailapp.feature.trash

/**
 * Pure confirmation seam for permanent delete.
 *
 * - [requestDelete] stores the pending ID, does NOT call Gmail.
 * - [confirmDelete] consumes the ID and invokes [onConfirmed] once.
 * - [cancelDelete] clears the pending ID without calling back.
 * - Does NOT offer undo (not even for restore).
 */
class TrashDeleteCoordinator {
    private var pendingDeleteId: String? = null

    val pendingDeleteEmailId: String? get() = pendingDeleteId
    val isDeletePending: Boolean get() = pendingDeleteId != null

    var onConfirmed: ((String) -> Unit)? = null

    fun requestDelete(emailId: String) {
        pendingDeleteId = emailId
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        pendingDeleteId = null
        onConfirmed?.invoke(id)
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }
}
