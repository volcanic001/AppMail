package com.david.mailapp.feature.compose

import com.david.mailapp.core.localization.FakeStringProvider
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/**
 * Tests JVM para [ComposeFormatUtils] y contratos de envío.
 *
 * No usa Android Context, Robolectric ni Mockito.
 * Usa [FakeStringProvider] con IDs de recurso simulados.
 */
class ComposeFormatUtilsTest {

    // ── Resource IDs de prueba ─────────────────────────────────────
    companion object {
        private const val ID_REPLY_PREFIX = 3001
        private const val ID_FWD_PREFIX = 3002
        private const val ID_REPLY_BODY = 3003
        private const val ID_FWD_HEADER = 3004
        private const val ID_FWD_FROM = 3005
        private const val ID_FWD_DATE = 3006
        private const val ID_FWD_SUBJECT = 3007
        private const val ID_FWD_TO = 3008
        private const val ID_DATE_PATTERN = 3009

        private val testEmail = Email(
            id = "msg1",
            threadId = "thread1",
            from = "Ana García <ana@example.com>",
            fromInitials = "AG",
            to = "destinatario@example.com",
            subject = "Informe mensual",
            snippet = "Adjunto el informe...",
            timestamp = 1700000000000L,
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            labels = emptyList(),
            folder = EmailFolder.Inbox,
            body = "",
            cleanBody = "<p>Adjunto el informe de este mes.</p>"
        )
    }

    private val testResources = mapOf(
        ID_REPLY_PREFIX to "Re: %1\$s",
        ID_FWD_PREFIX to "Fwd: %1\$s",
        ID_REPLY_BODY to "El %1\$s, %2\$s escribió:\n> %3\$s",
        ID_FWD_HEADER to "---------- Mensaje reenviado ----------",
        ID_FWD_FROM to "De: %1\$s",
        ID_FWD_DATE to "Fecha: %1\$s",
        ID_FWD_SUBJECT to "Asunto: %1\$s",
        ID_FWD_TO to "Para: %1\$s",
        ID_DATE_PATTERN to "d MMM yyyy, HH:mm"
    )

    private fun createUtils(stringProvider: FakeStringProvider): ComposeFormatUtils {
        return ComposeFormatUtils(
            stringProvider = stringProvider,
            resSubjectPrefixReply = ID_REPLY_PREFIX,
            resSubjectPrefixForward = ID_FWD_PREFIX,
            resComposeReplyBodyFormat = ID_REPLY_BODY,
            resComposeForwardHeader = ID_FWD_HEADER,
            resComposeForwardFieldFrom = ID_FWD_FROM,
            resComposeForwardFieldDate = ID_FWD_DATE,
            resComposeForwardFieldSubject = ID_FWD_SUBJECT,
            resComposeForwardFieldTo = ID_FWD_TO,
            resDatePatternShort = ID_DATE_PATTERN
        )
    }

    // ─────────────────────────────────────────────────────────────
    // buildReplySubject
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildReplySubject agrega Re prefijo y elimina duplicado`() {
        val provider = FakeStringProvider(testResources)
        val utils = createUtils(provider)

        // Sin prefijo existente
        assertEquals("Re: Informe mensual", utils.buildReplySubject("Informe mensual"))

        // Con prefijo Re: existente (debe eliminar duplicado)
        assertEquals("Re: Informe mensual", utils.buildReplySubject("Re: Informe mensual"))

        // Con prefijo RE: (case insensitive)
        assertEquals("Re: Informe mensual", utils.buildReplySubject("RE: Informe mensual"))

        // Argumentos enviados al provider
        val calls = provider.calls
        assertTrue(calls.any { it.resId == ID_REPLY_PREFIX && it.args.contains("Informe mensual") })
    }

    // ─────────────────────────────────────────────────────────────
    // buildForwardSubject
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildForwardSubject agrega Fwd prefijo y elimina duplicado`() {
        val provider = FakeStringProvider(testResources)
        val utils = createUtils(provider)

        // Sin prefijo existente
        assertEquals("Fwd: Informe mensual", utils.buildForwardSubject("Informe mensual"))

        // Con prefijo Fwd:
        assertEquals("Fwd: Informe mensual", utils.buildForwardSubject("Fwd: Informe mensual"))

        // Con prefijo Fw: (alias)
        assertEquals("Fwd: Informe mensual", utils.buildForwardSubject("Fw: Informe mensual"))

        // Case insensitive
        assertEquals("Fwd: Informe mensual", utils.buildForwardSubject("FWD: Informe mensual"))
    }

    // ─────────────────────────────────────────────────────────────
    // buildReplyBody
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildReplyBody construye cita con formato`() {
        val provider = FakeStringProvider(testResources)
        val utils = createUtils(provider)

        val result = utils.buildReplyBody("Gracias por el informe.", testEmail, "fallback")

        assertEquals(
            "Gracias por el informe.\n\n" +
                "El ${utils.formatTimestamp(testEmail.timestamp)}, " +
                "Ana García <ana@example.com> escribió:\n" +
                "> Adjunto el informe de este mes.",
            result
        )

        // Verificar que se llamó al provider
        val calls = provider.calls
        assertTrue(calls.any { it.resId == ID_REPLY_BODY })
    }

    @Test
    fun `buildReplyBody usa snippet como fallback cuando cleanBody vacio`() {
        val provider = FakeStringProvider(testResources)
        val utils = createUtils(provider)
        val emailSinBody = testEmail.copy(cleanBody = "")

        val result = utils.buildReplyBody("Ok.", emailSinBody, "snippet de respaldo")

        assertEquals(
            "Ok.\n\n" +
                "El ${utils.formatTimestamp(emailSinBody.timestamp)}, " +
                "Ana García <ana@example.com> escribió:\n" +
                "> snippet de respaldo",
            result
        )
    }

    // ─────────────────────────────────────────────────────────────
    // buildForwardBody
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildForwardBody construye encabezados y cuerpo`() {
        val provider = FakeStringProvider(testResources)
        val utils = createUtils(provider)

        val result = utils.buildForwardBody("Reenviando esto.", testEmail, "fallback")

        assertEquals(
            "Reenviando esto.\n\n" +
                "---------- Mensaje reenviado ----------\n" +
                "De: Ana García <ana@example.com>\n" +
                "Fecha: ${utils.formatTimestamp(testEmail.timestamp)}\n" +
                "Asunto: Informe mensual\n" +
                "Para: destinatario@example.com\n\n" +
                "Adjunto el informe de este mes.",
            result
        )

        // Verificar que se llamó al provider
        val calls = provider.calls
        assertTrue(calls.any { it.resId == ID_FWD_HEADER })
        assertTrue(calls.any { it.resId == ID_FWD_FROM })
        assertTrue(calls.any { it.resId == ID_FWD_DATE })
        assertTrue(calls.any { it.resId == ID_FWD_SUBJECT })
        assertTrue(calls.any { it.resId == ID_FWD_TO })
    }

    // ─────────────────────────────────────────────────────────────
    // formatTimestamp (companion) — con patrón, locale y timezone
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `formatTimestamp produce fecha exacta con patron locale y UTC`() {
        // 1700000000000L = Tuesday, 14 November 2023 22:13:20 UTC
        val result = ComposeFormatUtils.formatTimestamp(
            millis = 1700000000000L,
            pattern = "d MMM yyyy, HH:mm",
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertEquals("14 Nov 2023, 22:13", result)
    }

    @Test
    fun `formatTimestamp usa locale por defecto cuando no se especifica`() {
        val result = ComposeFormatUtils.formatTimestamp(
            millis = 1700000000000L,
            pattern = "yyyy-MM-dd HH:mm",
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertEquals("2023-11-14 22:13", result)
    }

    // ─────────────────────────────────────────────────────────────
    // htmlToPlainText (companion)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `htmlToPlainText limpia etiquetas HTML`() {
        val html = "<p>Hola <b>mundo</b></p>"
        val result = ComposeFormatUtils.htmlToPlainText(html)
        assertEquals("Hola mundo", result)
    }

    @Test
    fun `htmlToPlainText maneja null y blank`() {
        assertEquals("", ComposeFormatUtils.htmlToPlainText(null))
        assertEquals("", ComposeFormatUtils.htmlToPlainText(""))
        assertEquals("", ComposeFormatUtils.htmlToPlainText("   "))
    }

    @Test
    fun `htmlToPlainText separa parrafos con salto de linea`() {
        val html = "<p>Linea 1</p><p>Linea 2</p>"
        val result = ComposeFormatUtils.htmlToPlainText(html)
        // Jsoup inserta un newline tras cada </p> via doc.select("p").after("\n")
        assertTrue(result.contains("Linea 1"))
        assertTrue(result.contains("Linea 2"))
        assertTrue(result.contains("\n"))
    }

    // ─────────────────────────────────────────────────────────────
    // extractEmailAddress (companion)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `extractEmailAddress extrae direccion de brackets`() {
        assertEquals(
            "ana@example.com",
            ComposeFormatUtils.extractEmailAddress("Ana García <ana@example.com>")
        )
    }

    @Test
    fun `extractEmailAddress devuelve texto sin brackets intacto`() {
        assertEquals(
            "solo@email.com",
            ComposeFormatUtils.extractEmailAddress("solo@email.com")
        )
    }

    @Test
    fun `extractEmailAddress maneja solo nombre sin brackets`() {
        assertEquals(
            "Ana García",
            ComposeFormatUtils.extractEmailAddress("Ana García")
        )
    }

    // ─────────────────────────────────────────────────────────────
    // SendResult.Error contrato con UiErrorReason
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SendResult Error transporta UiErrorReason no String`() {
        val error = SendResult.Error(UiErrorReason.NO_CONNECTION)
        assertEquals(UiErrorReason.NO_CONNECTION, error.reason)
    }

    @Test
    fun `SendResult Error con SEND_FAILED`() {
        val error = SendResult.Error(UiErrorReason.SEND_FAILED)
        assertEquals(UiErrorReason.SEND_FAILED, error.reason)
    }

    // ─────────────────────────────────────────────────────────────
    // toComposeSendErrorReason — mapper específico de envío
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `IOException mapea a NO_CONNECTION en envio`() {
        assertEquals(
            UiErrorReason.NO_CONNECTION,
            IOException("timeout").toComposeSendErrorReason()
        )
    }

    @Test
    fun `OAuthSessionExpiredException mapea a SESSION_EXPIRED en envio`() {
        assertEquals(
            UiErrorReason.SESSION_EXPIRED,
            OAuthSessionExpiredException("expired").toComposeSendErrorReason()
        )
    }

    @Test
    fun `error desconocido mapea a SEND_FAILED en envio`() {
        assertEquals(
            UiErrorReason.SEND_FAILED,
            RuntimeException("algo raro").toComposeSendErrorReason()
        )
    }

    @Test
    fun `CancellationException se propaga en envio`() {
        var thrown = false
        try {
            CancellationException("cancelado").toComposeSendErrorReason()
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
