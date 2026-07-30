package com.david.mailapp.data.repository

import com.david.mailapp.core.localization.UiErrorReason

/**
 * Typed result of a Gmail–Room action (move, restore, delete, mark read).
 *
 * Replaces silent failure swallowing; the caller can decide UX based on
 * whether the remote side was applied.
 */
sealed class EmailActionResult {
    data object Success : EmailActionResult()
    data class Failure(
        val reason: UiErrorReason,
        val remoteApplied: Boolean
    ) : EmailActionResult()
}
