package com.david.mailapp.feature.compose

import com.david.mailapp.domain.model.Email

/**
 * Argumentos de navegación para [ComposeScreen].
 *
 * Contrato entre la navegación y el ViewModel.
 * No tiene dependencias de UI.
 */
sealed class ComposeArgs {
    /** Redactar un email nuevo desde cero. */
    data object Write : ComposeArgs()

    /**
     * Responder al remitente de [originalEmail].
     * El ViewModel prerellenará: Para = from, Asunto = "Re: …"
     */
    data class Reply(val originalEmail: Email) : ComposeArgs()

    /**
     * Reenviar [originalEmail] a nuevos destinatarios.
     * El ViewModel prerellenará: Asunto = "Fwd: …", Para vacío.
     */
    data class Forward(val originalEmail: Email) : ComposeArgs()
}
