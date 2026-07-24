package com.david.mailapp.core.localization

import com.david.mailapp.core.network.OAuthSessionExpiredException
import com.david.mailapp.data.pdf.PdfDownloadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Tests JVM para los contratos de localización (Fase 1B).
 *
 * No usa Android Context, Robolectric ni Mockito.
 */
class LocalizationTest {

    // ── Resource IDs de prueba ─────────────────────────────────────
    companion object {
        private const val ID_GREETING = 1001
        private const val ID_FORMATTED = 1002
        private const val ID_ERROR_GENERIC = 2001
        private const val ID_NO_CONNECTION = 2002
        private const val ID_UNUSED = 9999
    }

    private val testResources = mapOf(
        ID_GREETING to "Hola mundo",
        ID_FORMATTED to "Usuario: %1\$s, Edad: %2\$d",
        ID_ERROR_GENERIC to "Algo salio mal",
        ID_NO_CONNECTION to "Sin conexion a Internet"
    )

    private fun fakeProvider() = FakeStringProvider(testResources)

    // ─────────────────────────────────────────────────────────────
    // Tests 1–3: UiText + FakeStringProvider
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `UiText Resource sin argumentos se resuelve por su ID`() {
        val provider = fakeProvider()
        val uiText = UiText.Resource(ID_GREETING)

        val result = uiText.resolve(provider)

        assertEquals("Hola mundo", result)
    }

    @Test
    fun `recurso formateado conserva orden y valores de los argumentos`() {
        val provider = fakeProvider()
        val uiText = UiText.Resource(ID_FORMATTED, listOf("Ana", 30))

        val result = uiText.resolve(provider)

        assertEquals("Usuario: Ana, Edad: 30", result)
    }

    @Test
    fun `el fake registra exactamente una llamada`() {
        val provider = fakeProvider()
        val uiText = UiText.Resource(ID_GREETING)

        uiText.resolve(provider)

        assertEquals(1, provider.calls.size)
        assertEquals(ID_GREETING, provider.calls[0].resId)
        assertTrue(provider.calls[0].args.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // Tests 4–5: UiErrorReason → UiText.Resource
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `todos los valores de UiErrorReason estan cubiertos por el mapeo`() {
        val provider = fakeProvider()
        // Verificar que cada enum value produce un Resource sin lanzar
        for (reason in UiErrorReason.values()) {
            val resource = reason.toUiText()
            assertNotNull("toUiText() devolvio null para $reason", resource)
            // Solo verificamos que no lance: no podemos resolver sin R.string real
        }
        // Verificar que son 29 valores (contra el enum actual)
        assertEquals(29, UiErrorReason.values().size)
    }

    @Test
    fun `todos los errores generan UiText Resource con formatArgs vacio`() {
        for (reason in UiErrorReason.values()) {
            val resource = reason.toUiText()
            assertTrue(
                "El resource para $reason deberia tener formatArgs vacio, pero tiene ${resource.formatArgs}",
                resource.formatArgs.isEmpty()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tests 6–10: Throwable.toUiErrorReason()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RuntimeException con mensaje secreto se convierte en UNKNOWN y el texto no aparece`() {
        val ex = RuntimeException("token secreto")

        val reason = ex.toUiErrorReason()

        assertEquals(UiErrorReason.UNKNOWN, reason)
    }

    @Test
    fun `IOException incluso como causa anidada produce NO_CONNECTION`() {
        val ex = RuntimeException("Envoltorio", IOException("Red caida"))

        val reason = ex.toUiErrorReason()

        assertEquals(UiErrorReason.NO_CONNECTION, reason)
    }

    @Test
    fun `OAuthSessionExpiredException incluso anidada produce SESSION_EXPIRED`() {
        val ex = RuntimeException(
            "Envoltorio",
            OAuthSessionExpiredException("Token expired")
        )

        val reason = ex.toUiErrorReason()

        assertEquals(UiErrorReason.SESSION_EXPIRED, reason)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `CancellationException se propaga`() {
        val ex = kotlinx.coroutines.CancellationException("Operacion cancelada")

        ex.toUiErrorReason()
    }

    @Test
    fun `ciclo artificial en cadena de causas no provoca bucle infinito y termina en UNKNOWN`() {
        // Creamos un ciclo manual sin usar reflection (inaccesible en Java 17+)
        val cyclic = CyclicCauseException()
        val mid = RuntimeException("intermedio", cyclic)
        val top = RuntimeException("superior", mid)
        cyclic.attachCycle(top)

        val reason = top.toUiErrorReason()

        assertEquals(UiErrorReason.UNKNOWN, reason)
    }

    // ─────────────────────────────────────────────────────────────
    // Test 11: PdfDownloadFailure.toUiErrorReason()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `los seis valores de PdfDownloadFailure producen el resultado establecido`() {
        val map = mapOf(
            PdfDownloadFailure.TOO_LARGE to UiErrorReason.PDF_TOO_LARGE,
            PdfDownloadFailure.INVALID_PDF to UiErrorReason.PDF_INVALID,
            PdfDownloadFailure.EMPTY_CONTENT to UiErrorReason.PDF_INVALID,
            PdfDownloadFailure.NO_PROVIDER to UiErrorReason.NO_ACTIVE_ACCOUNT,
            PdfDownloadFailure.NETWORK to UiErrorReason.PDF_DOWNLOAD_FAILED,
            PdfDownloadFailure.CACHE_WRITE to UiErrorReason.PDF_DOWNLOAD_FAILED
        )

        for ((failure, expected) in map) {
            val actual = failure.toUiErrorReason()
            assertEquals("PdfDownloadFailure.$failure deberia mapear a $expected", expected, actual)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Test 12: FakeStringProvider falla con recurso desconocido
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `el fake falla ante un recurso desconocido`() {
        val provider = fakeProvider()

        try {
            provider.getString(ID_UNUSED)
            fail("Deberia haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Esperado
            assertTrue(e.message?.contains("$ID_UNUSED") ?: false)
        }
    }
}

/**
 * Excepción helper para crear ciclos en la cadena de causas sin usar
 * reflection (inaccesible en Java 17+).
 *
 * [attachCycle] asigna [cause] manualmente para cerrar el ciclo.
 */
private class CyclicCauseException : RuntimeException("causa raiz") {
    private var cycleTarget: Throwable? = null

    fun attachCycle(target: Throwable) {
        cycleTarget = target
    }

    override val cause: Throwable?
        get() = cycleTarget ?: super.cause
}
