package com.david.mailapp.core.localization

import com.david.mailapp.R
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.data.pdf.PdfDownloadFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Razones semánticas de error conocidas.
 *
 * Sin campos String, mensajes ni excepciones.
 * Cada valor se mapea a un [UiText.Resource] mediante [toUiText].
 */
enum class UiErrorReason {
    UNKNOWN,
    NO_CONNECTION,
    SESSION_EXPIRED,
    TEMP_CLEANUP_FAILED,
    LOCAL_CLEANUP_CHECK_FAILED,
    NO_COMPATIBLE_BROWSER,
    AUTH_LAUNCH_FAILED,
    OAUTH_INVALID_SESSION,
    SIGN_IN_FAILED,
    SIGN_OUT_IN_PROGRESS,
    SIGN_OUT_FAILED,
    NO_ACTIVE_ACCOUNT,
    SENDER_ADDRESS_UNAVAILABLE,
    SEND_FAILED,
    EMAIL_NOT_FOUND,
    EMAIL_BODY_LOAD_FAILED,
    EMAIL_BODY_PDFS_ONLY,
    IMAGE_INVALID_FORMAT,
    IMAGE_LOAD_FAILED,
    IMAGE_SAVE_FAILED,
    PDF_TOO_LARGE,
    PDF_INVALID,
    PDF_DOWNLOAD_FAILED,
    PDF_CACHE_EXPIRED,
    PDF_SAVE_FAILED,
    PDF_FILE_PICKER_UNAVAILABLE,
    PDF_FILE_PICKER_FAILED,
    PDF_VIEWER_UNAVAILABLE,
    PDF_OPEN_FAILED
}

/**
 * Mapea [UiErrorReason] a [UiText.Resource].
 *
 * Los errores creados desde aquí siempre producen Resource sin argumentos
 * de formato, salvo que el mapeo lo requiera explícitamente.
 * Nunca acepta un mensaje alternativo ni copia datos técnicos.
 */
fun UiErrorReason.toUiText(): UiText.Resource {
    return when (this) {
        UiErrorReason.UNKNOWN -> UiText.Resource(R.string.error_generic)
        UiErrorReason.NO_CONNECTION -> UiText.Resource(R.string.error_no_connection)
        UiErrorReason.SESSION_EXPIRED -> UiText.Resource(R.string.session_expired)
        UiErrorReason.TEMP_CLEANUP_FAILED -> UiText.Resource(R.string.session_temp_cleanup_failed)
        UiErrorReason.LOCAL_CLEANUP_CHECK_FAILED -> UiText.Resource(R.string.session_local_cleanup_check_failed)
        UiErrorReason.NO_COMPATIBLE_BROWSER -> UiText.Resource(R.string.session_no_browser)
        UiErrorReason.AUTH_LAUNCH_FAILED -> UiText.Resource(R.string.session_auth_launch_failed)
        UiErrorReason.OAUTH_INVALID_SESSION -> UiText.Resource(R.string.session_oauth_invalid)
        UiErrorReason.SIGN_IN_FAILED -> UiText.Resource(R.string.session_signin_failed)
        UiErrorReason.SIGN_OUT_IN_PROGRESS -> UiText.Resource(R.string.session_signout_in_progress)
        UiErrorReason.SIGN_OUT_FAILED -> UiText.Resource(R.string.session_signout_failed)
        UiErrorReason.NO_ACTIVE_ACCOUNT -> UiText.Resource(R.string.error_no_active_account)
        UiErrorReason.SENDER_ADDRESS_UNAVAILABLE -> UiText.Resource(R.string.error_sender_address_unavailable)
        UiErrorReason.SEND_FAILED -> UiText.Resource(R.string.compose_send_error)
        UiErrorReason.EMAIL_NOT_FOUND -> UiText.Resource(R.string.detail_email_not_found)
        UiErrorReason.EMAIL_BODY_LOAD_FAILED -> UiText.Resource(R.string.detail_body_load_error)
        UiErrorReason.EMAIL_BODY_PDFS_ONLY -> UiText.Resource(R.string.detail_body_pdfs_only)
        UiErrorReason.IMAGE_INVALID_FORMAT -> UiText.Resource(R.string.image_invalid_format)
        UiErrorReason.IMAGE_LOAD_FAILED -> UiText.Resource(R.string.image_load_error)
        UiErrorReason.IMAGE_SAVE_FAILED -> UiText.Resource(R.string.image_save_error)
        UiErrorReason.PDF_TOO_LARGE -> UiText.Resource(R.string.pdf_too_large)
        UiErrorReason.PDF_INVALID -> UiText.Resource(R.string.pdf_invalid)
        UiErrorReason.PDF_DOWNLOAD_FAILED -> UiText.Resource(R.string.pdf_download_failed)
        UiErrorReason.PDF_CACHE_EXPIRED -> UiText.Resource(R.string.pdf_cache_expired)
        UiErrorReason.PDF_SAVE_FAILED -> UiText.Resource(R.string.pdf_save_failed)
        UiErrorReason.PDF_FILE_PICKER_UNAVAILABLE -> UiText.Resource(R.string.pdf_no_file_picker)
        UiErrorReason.PDF_FILE_PICKER_FAILED -> UiText.Resource(R.string.pdf_picker_open_failed)
        UiErrorReason.PDF_VIEWER_UNAVAILABLE -> UiText.Resource(R.string.pdf_no_viewer)
        UiErrorReason.PDF_OPEN_FAILED -> UiText.Resource(R.string.pdf_open_failed)
    }
}

/**
 * Convierte un [Throwable] en [UiErrorReason] de forma segura.
 *
 * Reglas:
 * 1. Recorre la cadena de causas con protección contra ciclos.
 * 2. [CancellationException] se propaga tal cual.
 * 3. [OAuthSessionExpiredException] → [UiErrorReason.SESSION_EXPIRED].
 * 4. [IOException] → [UiErrorReason.NO_CONNECTION].
 * 5. Cualquier otro error → [UiErrorReason.UNKNOWN].
 * 6. Nunca consulta [Throwable.message], nombre de clase, código HTTP
 *    ni cuerpo remoto.
 */
fun Throwable.toUiErrorReason(): UiErrorReason {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null) {
        if (!seen.add(current)) break // ciclo detectado
        when (current) {
            is CancellationException -> throw current
            is OAuthSessionExpiredException -> return UiErrorReason.SESSION_EXPIRED
            is IOException -> return UiErrorReason.NO_CONNECTION
        }
        current = current.cause
    }
    return UiErrorReason.UNKNOWN
}

/**
 * Convierte un [PdfDownloadFailure] en [UiErrorReason].
 */
fun PdfDownloadFailure.toUiErrorReason(): UiErrorReason {
    return when (this) {
        PdfDownloadFailure.TOO_LARGE -> UiErrorReason.PDF_TOO_LARGE
        PdfDownloadFailure.INVALID_PDF,
        PdfDownloadFailure.EMPTY_CONTENT -> UiErrorReason.PDF_INVALID
        PdfDownloadFailure.NO_PROVIDER -> UiErrorReason.NO_ACTIVE_ACCOUNT
        PdfDownloadFailure.NETWORK,
        PdfDownloadFailure.CACHE_WRITE -> UiErrorReason.PDF_DOWNLOAD_FAILED
    }
}
