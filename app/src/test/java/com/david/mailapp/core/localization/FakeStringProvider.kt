package com.david.mailapp.core.localization

import androidx.annotation.StringRes

/**
 * Fake de [StringProvider] para pruebas JVM.
 *
 * - Recibe un mapa de ID de recurso → template string.
 * - Registra cada llamada: ID y argumentos.
 * - Sin argumentos devuelve el valor registrado.
 * - Con argumentos usa [String.format] con [java.util.Locale.ROOT].
 * - Falla con [IllegalArgumentException] si se solicita un ID no registrado.
 * - No usa Android Context, Robolectric ni Mockito.
 */
class FakeStringProvider(
    private val resources: Map<Int, String>
) : StringProvider {

    data class Call(
        @StringRes val resId: Int,
        val args: List<Any>
    )

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls.toList()

    override fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val template = resources[resId]
            ?: throw IllegalArgumentException("Resource ID $resId no registrado en FakeStringProvider")

        _calls.add(Call(resId, formatArgs.toList()))

        return if (formatArgs.isEmpty()) {
            template
        } else {
            String.format(java.util.Locale.ROOT, template, *formatArgs)
        }
    }
}
