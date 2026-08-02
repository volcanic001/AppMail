package com.david.mailapp.feature.compose

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
     * Responder al remitente del email original.
     * El ViewModel prerellenará: Para = from, Asunto = "Re: …"
     */
    data class Reply(val originalEmailId: String) : ComposeArgs() {
        init {
            require(originalEmailId.isNotBlank()) {
                "originalEmailId must not be empty or blank"
            }
        }
    }

    /**
     * Reenviar el email original a nuevos destinatarios.
     * El ViewModel prerellenará: Asunto = "Fwd: …", Para vacío.
     */
    data class Forward(val originalEmailId: String) : ComposeArgs() {
        init {
            require(originalEmailId.isNotBlank()) {
                "originalEmailId must not be empty or blank"
            }
        }
    }
}
