package com.david.mailapp.core.localization

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Representación tipo-safe de un texto visible en la UI.
 *
 * Las implementaciones conocidas son [Resource].
 * No existe variante Dynamic, Raw, Plain ni equivalente.
 */
sealed interface UiText {

    /**
     * Texto definido como recurso Android con ID y argumentos opcionales.
     *
     * @param resId      ID del recurso string en strings.xml.
     * @param formatArgs Argumentos posicionales para [String.format].
     *                   Solo para datos legítimamente variables (consulta,
     *                   cantidad, fecha, remitente). No para excepciones,
     *                   respuestas HTTP ni mensajes técnicos.
     */
    data class Resource(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList()
    ) : UiText
}

/**
 * Resuelve un [UiText] a [String] usando un [StringProvider].
 */
fun UiText.resolve(stringProvider: StringProvider): String {
    return when (this) {
        is UiText.Resource -> stringProvider.getString(resId, *formatArgs.toTypedArray())
    }
}

/**
 * Resuelve un [UiText] a [String] usando [stringResource] de Compose.
 */
@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Resource -> stringResource(id = resId, formatArgs = formatArgs.toTypedArray())
    }
}
