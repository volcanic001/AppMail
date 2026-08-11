package com.david.mailapp.data.repository

import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult

/**
 * Simple provider delegation for operations that do not touch Room,
 * PDF cache, or session writes. The provider is resolved per call via
 * [providerFactory] so it stays fresh after sign-in/sign-out.
 */
internal class EmailProviderGateway(
    private val providerFactory: () -> EmailProvider?
) {
    /** Remote search — NOT cached in Room. Results are ephemeral and live only in the ViewModel's state. */
    suspend fun searchEmails(query: String, pageToken: String? = null): PaginatedResult<Email> {
        val p = providerFactory() ?: return PaginatedResult(emptyList(), null)
        return p.search(query, pageToken)
    }

    /** Obtiene la dirección de email del usuario autenticado desde el provider. */
    suspend fun getUserEmail(): String? = providerFactory()?.getUserEmail()

    suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext? = null
    ) {
        providerFactory()?.sendEmail(to, cc, bcc, subject, body, replyContext)
            ?: error("No hay proveedor activo")
    }
}
