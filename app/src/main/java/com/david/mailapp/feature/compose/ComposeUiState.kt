package com.david.mailapp.feature.compose

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email

enum class ComposeMode { WRITE, REPLY, FORWARD }

sealed class SendResult {
    data object Success : SendResult()
    data class Error(val reason: UiErrorReason) : SendResult()
}

/**
 * Estado observable completo de la pantalla de composición.
 *
 * El ViewModel expone este objeto como [kotlinx.coroutines.flow.StateFlow];
 * la UI lo consume sin conocer la lógica de negocio.
 */
data class ComposeUiState(
    // ── Campos del formulario ────────────────────────────────
    val fromAddress: String = "",
    val toField: String = "",
    val ccField: String = "",
    val bccField: String = "",
    val subject: String = "",
    val bodyText: String = "",

    // ── Control de UI ────────────────────────────────────────
    val isCcBccExpanded: Boolean = false,
    val isSending: Boolean = false,
    val sendResult: SendResult? = null,

    // ── Contexto del email original (Reply / Forward) ────────
    val originalEmail: Email? = null,
    val composeMode: ComposeMode = ComposeMode.WRITE
)
