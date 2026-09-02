package com.david.mailapp.feature.emaildetail

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email

/**
 * UI state for the email detail screen.
 *
 * The ViewModel first resolves the email via [EmailRepository.resolveEmailById]
 * (cache → Gmail → Room), then observes Room reactively. This guarantees that
 * a null Room emission never produces "Email no encontrado" — only a confirmed
 * NotFound from Gmail does.
 */
sealed interface EmailDetailUiState {

    /** Resolution is in progress — UI shows a centered spinner. */
    data object Loading : EmailDetailUiState

    /**
     * Resolution completed but the email was not found remotely.
     * [reason] carries the typed failure, [retryable] controls whether
     * the on-screen retry button is shown.
     */
    data class ResolutionError(
        val reason: UiErrorReason,
        val retryable: Boolean
    ) : EmailDetailUiState

    /**
     * Metadata available, body is being prepared (remote fetch or inline-image
     * resolution in progress). The header / TopAppBar can render normally,
     * but the WebView must NOT load HTML in this state.
     */
    data class PreparingBody(val email: Email) : EmailDetailUiState

    /**
     * Body fully prepared — remote body fetched (if needed) and inline images
     * resolved (or failed, falling back to the original body). This is the
     * ONLY state in which the WebView may receive the HTML document.
     */
    data class Ready(
        val email: Email,
        val inlineImagesLoading: Boolean = false
    ) : EmailDetailUiState

    /** MIME parsing completed successfully but the message has no visible body. */
    data class Empty(val email: Email) : EmailDetailUiState

    /**
     * Body-level error. [email] may be non-null when metadata was already
     * available before the fetch failed. [retryable] is true when the body
     * fetch failed transiently and can be retried without re-resolving.
     */
    data class BodyError(
        val email: Email?,
        val reason: UiErrorReason,
        val retryable: Boolean = false
    ) : EmailDetailUiState
}
