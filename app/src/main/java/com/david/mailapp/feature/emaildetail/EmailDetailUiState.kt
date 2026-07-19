package com.david.mailapp.feature.emaildetail

import com.david.mailapp.domain.model.Email

/**
 * UI state for the email detail screen.
 *
 * The ViewModel observes a single email from Room and guarantees exactly ONE
 * [Ready] delivery per email, after the body is fully prepared (remote fetch
 * + inline-image injection). While the body is being prepared the screen shows
 * [PreparingBody] — metadata is available for the header, but the WebView
 * must NOT receive HTML until [Ready].
 */
sealed interface EmailDetailUiState {

    /** No data yet — Room query hasn't emitted. UI shows a centered spinner. */
    data object Loading : EmailDetailUiState

    /**
     * Metadata available, body is being prepared (remote fetch or inline-image
     * resolution in progress).  The header / TopAppBar can render normally,
     * but the WebView must NOT load HTML in this state.
     */
    data class PreparingBody(val email: Email) : EmailDetailUiState

    /**
     * Body fully prepared — remote body fetched (if needed) and inline images
     * resolved (or failed, falling back to the original body).  This is the
     * ONLY state in which the WebView may receive the HTML document.
     */
    data class Ready(
        val email: Email,
        val inlineImagesLoading: Boolean = false
    ) : EmailDetailUiState

    /**
     * Body-level error. [email] may be non-null when metadata was already
     * available before the fetch failed.
     */
    data class BodyError(val email: Email?, val message: String) : EmailDetailUiState
}
