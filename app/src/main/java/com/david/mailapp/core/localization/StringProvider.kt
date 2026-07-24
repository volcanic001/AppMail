package com.david.mailapp.core.localization

import android.content.Context
import androidx.annotation.StringRes

/**
 * Abstracción para resolver recursos string de Android.
 *
 * Permite que capas no-Android (ViewModels, lógica de dominio) resuelvan
 * textos sin depender de Context ni de Compose.
 */
interface StringProvider {

    /**
     * Obtiene el string correspondiente a [resId], expandiendo [formatArgs]
     * con [String.format].
     */
    fun getString(
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String
}

/**
 * Implementación de [StringProvider] que delega en [Context.getString].
 *
 * Conserva [applicationContext] para evitar fugas de Activity.
 * No captura excepciones de recursos ni aplica fallbacks silenciosos.
 */
class AndroidStringProvider(context: Context) : StringProvider {

    private val appContext = context.applicationContext

    override fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return if (formatArgs.isEmpty()) {
            appContext.getString(resId)
        } else {
            appContext.getString(resId, *formatArgs)
        }
    }
}
