package com.david.mailapp.feature.compose

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.network.OAuthSessionExpiredException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Mapeo específico para errores de envío en Compose.
 *
 * A diferencia de [Throwable.toUiErrorReason] (que usa [UiErrorReason.UNKNOWN]
 * como fallback), este mapper convierte errores desconocidos en
 * [UiErrorReason.SEND_FAILED] para que la UI muestre "Error al enviar"
 * en lugar de "Algo salió mal".
 *
 * Reglas:
 * 1. [CancellationException] se propaga.
 * 2. [OAuthSessionExpiredException] → [UiErrorReason.SESSION_EXPIRED].
 * 3. [IOException] → [UiErrorReason.NO_CONNECTION].
 * 4. Cualquier otro error → [UiErrorReason.SEND_FAILED].
 * 5. Nunca consulta [Throwable.message].
 */
internal fun Throwable.toComposeSendErrorReason(): UiErrorReason {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null) {
        if (!seen.add(current)) break
        when (current) {
            is CancellationException -> throw current
            is OAuthSessionExpiredException -> return UiErrorReason.SESSION_EXPIRED
            is IOException -> return UiErrorReason.NO_CONNECTION
        }
        current = current.cause
    }
    return UiErrorReason.SEND_FAILED
}
