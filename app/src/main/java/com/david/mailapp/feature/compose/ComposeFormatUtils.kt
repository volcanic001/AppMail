package com.david.mailapp.feature.compose

import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Funciones puras de formato para la pantalla de composición.
 *
 * Sin estado, fáciles de testear unitariamente.
 */
object ComposeFormatUtils {

    private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    /** Formatea un timestamp Long a "15 may 2024, 14:30". */
    fun formatTimestamp(millis: Long): String = dateFormat.format(Date(millis))

    /** Extrae la dirección pura de "John Doe <john@example.com>" → "john@example.com". */
    fun extractEmailAddress(from: String): String {
        val inBrackets = from.substringAfter("<").substringBefore(">").trim()
        return inBrackets.ifBlank { from.trim() }
    }

    /** "Re: Asunto" sin duplicar el prefijo "Re:". */
    fun buildReplySubject(original: String): String {
        val stripped = original.replaceFirst(Regex("(?i)^Re:\\s*"), "")
        return "Re: $stripped"
    }

    /** "Fwd: Asunto" sin duplicar el prefijo "Fwd:" / "Fw:". */
    fun buildForwardSubject(original: String): String {
        val stripped = original.replaceFirst(Regex("(?i)^(Fwd|Fw):\\s*"), "")
        return "Fwd: $stripped"
    }

    /**
     * Convierte una cadena HTML cruda o limpia en texto plano legible,
     * eliminando estilos CSS, metadatos, scripts y etiquetas HTML.
     */
    fun htmlToPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return try {
            val doc = Jsoup.parse(html)
            doc.select("head, style, script, meta, link").remove()
            doc.outputSettings().prettyPrint(false)
            doc.select("br").after("\n")
            doc.select("p, div, h1, h2, h3, h4, h5, h6, tr, li").after("\n")
            val rawText = doc.body().wholeText()
            val lines = rawText.lines().map { it.trim() }
            lines.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
        } catch (e: Exception) {
            html.replace(Regex("<[^>]*>"), "").trim()
        }
    }
}

