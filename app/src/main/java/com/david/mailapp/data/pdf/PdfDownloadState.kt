package com.david.mailapp.data.pdf

/**
 * UI-friendly representation of a single PDF attachment's download status.
 *
 * El ViewModel mantiene un Map<partId estable, PdfDownloadState>.
 * La UI nunca recibe bytes, rutas absolutas ni excepciones de red.
 */
sealed interface PdfDownloadState {

    /** No se ha iniciado la descarga — estado inicial. */
    data object Idle : PdfDownloadState

    /** Descarga en curso — indicador indeterminado. */
    data object Downloading : PdfDownloadState

    /** Descarga completada y archivada en caché privado. */
    data class Ready(val sizeBytes: Long) : PdfDownloadState

    /** Fallo en la descarga o validación. */
    data class Error(val reason: PdfDownloadFailure) : PdfDownloadState
}

/**
 * Causas de fallo durante la descarga de un PDF.
 *
 * NO_PROVIDER  — No hay proveedor activo (usuario no autenticado).
 * TOO_LARGE    — El archivo supera el límite de 25 MiB (declarado o real).
 * EMPTY_CONTENT— El servidor devolvió cero bytes.
 * INVALID_PDF  — El contenido no comienza con %PDF-.
 * NETWORK      — Error de red o HTTP al descargar.
 * CACHE_WRITE  — Error al escribir el archivo en caché.
 */
enum class PdfDownloadFailure {
    NO_PROVIDER,
    TOO_LARGE,
    EMPTY_CONTENT,
    INVALID_PDF,
    NETWORK,
    CACHE_WRITE
}
