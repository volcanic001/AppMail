package com.david.mailapp.feature.emaildetail

/**
 * Sanitiza el nombre visible para el visor externo:
 * elimina separadores de ruta, caracteres de control y espacios extremos.
 * Si queda vacío, usa [defaultName].
 */
internal fun sanitizeDisplayName(name: String, defaultName: String): String {
    val sanitized = name
        .replace("/", "_")
        .replace("\\", "_")
        .replace(Regex("[\\x00-\\x1f\\x7f]"), "")
        .trim()
    return sanitized.ifBlank { defaultName }
}

/**
 * Construye el nombre sugerido para el selector SAF.
 * Sanitiza [displayName] y garantiza que termine en `.pdf`.
 */
internal fun buildPdfSuggestedName(displayName: String, defaultName: String): String {
    val sanitized = sanitizeDisplayName(displayName, defaultName)
    return if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
}
