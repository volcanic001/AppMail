package com.david.mailapp.feature.emaildetail

import com.david.mailapp.domain.model.Email

/**
 * Claims the single mark-as-read attempt owned by one EmailDetailViewModel.
 *
 * The gate is intentionally free of coroutines, repositories and UI state.
 * [EmailReadOnOpenCoordinator] calls [claim] before launching the remote-first
 * action so repeated Room emissions cannot start duplicate Gmail requests.
 */
internal class EmailReadOnOpenGate {

    private var requestClaimed = false

    fun claim(email: Email): Boolean {
        if (email.isRead || requestClaimed) return false
        requestClaimed = true
        return true
    }
}
