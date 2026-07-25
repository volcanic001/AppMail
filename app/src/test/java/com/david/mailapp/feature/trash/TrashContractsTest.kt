package com.david.mailapp.feature.trash

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.network.OAuthSessionExpiredException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests JVM para los contratos de TrashUiState.
 *
 * Verifica que los estados Error transportan UiErrorReason, no strings,
 * y que el mapeo de excepciones produce las razones semánticas esperadas.
 */
class TrashContractsTest {

    @Test
    fun `TrashUiState Error transporta UiErrorReason no String`() {
        val error = TrashUiState.Error(UiErrorReason.NO_CONNECTION)
        assertEquals(UiErrorReason.NO_CONNECTION, error.reason)
    }

    @Test
    fun `IOException produce NO_CONNECTION`() {
        assertEquals(
            UiErrorReason.NO_CONNECTION,
            IOException("Network is unreachable").toUiErrorReason()
        )
    }

    @Test
    fun `OAuthSessionExpiredException produce SESSION_EXPIRED`() {
        assertEquals(
            UiErrorReason.SESSION_EXPIRED,
            OAuthSessionExpiredException("token expired").toUiErrorReason()
        )
    }

    @Test
    fun `RuntimeException produce UNKNOWN`() {
        assertEquals(
            UiErrorReason.UNKNOWN,
            RuntimeException("something broke").toUiErrorReason()
        )
    }

    @Test
    fun `CancellationException se propaga`() {
        var thrown = false
        try {
            CancellationException("cancelled").toUiErrorReason()
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `IOException anidada como causa produce NO_CONNECTION`() {
        val cause = IOException("timeout")
        val wrapper = RuntimeException("outer", cause)
        assertEquals(UiErrorReason.NO_CONNECTION, wrapper.toUiErrorReason())
    }

    @Test
    fun `cadena de causas con ciclo termina en UNKNOWN`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals(UiErrorReason.UNKNOWN, a.toUiErrorReason())
    }
}
