package com.david.mailapp.feature.emaildetail

/**
 * Solicitud de acción externa sobre un PDF emitida por [EmailDetailViewModel].
 *
 * La UI recoge el único Flow respaldado por Channel.CONFLATED y ejecuta
 * la acción correspondiente (abrir visor o guardar documento).
 */
sealed interface PdfExternalActionRequest {

    val emailId: String
    val stablePartId: String
    val displayName: String

    /** Abrir el PDF con el visor externo (Intent.ACTION_VIEW). */
    data class Open(
        override val emailId: String,
        override val stablePartId: String,
        override val displayName: String
    ) : PdfExternalActionRequest

    /** Guardar el PDF mediante CreateDocument (SAF). */
    data class Save(
        override val emailId: String,
        override val stablePartId: String,
        override val displayName: String
    ) : PdfExternalActionRequest
}
