package com.david.mailapp.feature.compose

import com.david.mailapp.R
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.domain.model.Email
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Funciones de formato localizadas para la pantalla de composición.
 *
 * Las funciones que requieren resolución de recursos ([StringProvider]) son métodos
 * de instancia. Las funciones sin dependencias de localización están en [Companion].
 */
class ComposeFormatUtils(
    private val stringProvider: StringProvider,
    /** Resource IDs inyectables para testing JVM sin R.android. */
    private val resSubjectPrefixReply: Int = R.string.subject_prefix_reply,
    private val resSubjectPrefixForward: Int = R.string.subject_prefix_forward,
    private val resComposeReplyBodyFormat: Int = R.string.compose_reply_body_format,
    private val resComposeForwardHeader: Int = R.string.compose_forward_header,
    private val resComposeForwardFieldFrom: Int = R.string.compose_forward_field_from,
    private val resComposeForwardFieldDate: Int = R.string.compose_forward_field_date,
    private val resComposeForwardFieldSubject: Int = R.string.compose_forward_field_subject,
    private val resComposeForwardFieldTo: Int = R.string.compose_forward_field_to,
    private val resDatePatternShort: Int = R.string.date_pattern_short
) {

    // ── Dependencia de recursos ─────────────────────────────────

    private val dateFormat: SimpleDateFormat by lazy {
        val pattern = stringProvider.getString(resDatePatternShort)
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    /** Formatea un timestamp Long con el patrón localizado (date_pattern_short). */
    fun formatTimestamp(millis: Long): String = dateFormat.format(Date(millis))

    /** "Re: Asunto" sin duplicar el prefijo, usando subject_prefix_reply. */
    fun buildReplySubject(original: String): String {
        val stripped = original.replaceFirst(Regex("(?i)^Re:\\s*"), "")
        return stringProvider.getString(resSubjectPrefixReply, stripped)
    }

    /** "Fwd: Asunto" sin duplicar el prefijo, usando subject_prefix_forward. */
    fun buildForwardSubject(original: String): String {
        val stripped = original.replaceFirst(Regex("(?i)^(Fwd|Fw):\\s*"), "")
        return stringProvider.getString(resSubjectPrefixForward, stripped)
    }

    /**
     * Construye el cuerpo completo de un Reply con cita formateada.
     *
     * Resultado: [newBody]\n\nEl [fecha], [remitente] escribió:\n> [cita]
     */
    fun buildReplyBody(newBody: String, original: Email, cleanFallback: String): String {
        val cleanText = htmlToPlainText(original.cleanBody.ifBlank { cleanFallback })
        val quoted = cleanText.lines().joinToString("\n> ")
        val dateStr = formatTimestamp(original.timestamp)
        val quotedSection = stringProvider.getString(
            resComposeReplyBodyFormat,
            dateStr,
            original.from,
            quoted
        )
        return "$newBody\n\n$quotedSection"
    }

    /**
     * Construye el cuerpo completo de un Forward con encabezados y mensaje original.
     *
     * Resultado: [newBody]\n\n---------- Mensaje reenviado ----------\n
     * De: [from]\nFecha: [date]\nAsunto: [subject]\nPara: [to]\n\n[body]
     */
    fun buildForwardBody(newBody: String, original: Email, cleanFallback: String): String {
        val cleanText = htmlToPlainText(original.cleanBody.ifBlank { cleanFallback })
        val dateStr = formatTimestamp(original.timestamp)
        val header = stringProvider.getString(resComposeForwardHeader)
        val fromLine = stringProvider.getString(resComposeForwardFieldFrom, original.from)
        val dateLine = stringProvider.getString(resComposeForwardFieldDate, dateStr)
        val subjectLine = stringProvider.getString(
            resComposeForwardFieldSubject,
            original.subject
        )
        val toLine = stringProvider.getString(resComposeForwardFieldTo, original.to)
        return "$newBody\n\n$header\n$fromLine\n$dateLine\n$subjectLine\n$toLine\n\n$cleanText"
    }

    // ── Funciones puras (compañera) ──────────────────────────────

    companion object {
        /**
         * Formatea un timestamp Long con patrón, locale y zona horaria explícitos.
         *
         * @param millis  Timestamp en milisegundos desde epoch.
         * @param pattern Patrón [SimpleDateFormat] (ej. "d MMM yyyy, HH:mm").
         * @param locale  [Locale] para el formato (por defecto [Locale.getDefault]).
         * @param timeZone [TimeZone] para el formato (por defecto [TimeZone.getDefault]).
         */
        @JvmStatic
        fun formatTimestamp(
            millis: Long,
            pattern: String,
            locale: Locale = Locale.getDefault(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): String {
            val fmt = SimpleDateFormat(pattern, locale)
            fmt.timeZone = timeZone
            return fmt.format(Date(millis))
        }
        /** Extrae la dirección pura de "John Doe <john@example.com>" → "john@example.com". */
        fun extractEmailAddress(from: String): String {
            val inBrackets = from.substringAfter("<").substringBefore(">").trim()
            return inBrackets.ifBlank { from.trim() }
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
}
