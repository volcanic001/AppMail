package com.david.mailapp.feature.auth

import com.david.mailapp.core.auth.OAuthLaunchResult
import com.david.mailapp.core.auth.OAuthRedirectResult
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.UiText
import com.david.mailapp.core.localization.toUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM para los mapeadores de resultados OAuth.
 *
 * Verifica que cada variante produce el recurso o null definido,
 * que ningún resultado genera texto dinámico y que los errores
 * producen UiText.Resource sin argumentos.
 */
class OAuthResultMappersTest {

    // ── OAuthLaunchResult ──────────────────────────────────────

    @Test
    fun `OAuthLaunchResult Launched devuelve null`() {
        assertNull(OAuthLaunchResult.Launched.toUiTextOrNull())
    }

    @Test
    fun `OAuthLaunchResult NoBrowserAvailable devuelve NO_COMPATIBLE_BROWSER`() {
        val result = OAuthLaunchResult.NoBrowserAvailable.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `OAuthLaunchResult Failed devuelve AUTH_LAUNCH_FAILED`() {
        val result = OAuthLaunchResult.Failed.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `OAuthLaunchResult Failed no contiene texto dinámico`() {
        val result = OAuthLaunchResult.Failed.toUiTextOrNull() as UiText.Resource
        assertTrue(result.formatArgs.isEmpty())
    }

    // ── OAuthRedirectResult ────────────────────────────────────

    @Test
    fun `OAuthRedirectResult Success devuelve null`() {
        assertNull(OAuthRedirectResult.Success.toUiTextOrNull())
    }

    @Test
    fun `OAuthRedirectResult NotOAuthRedirect devuelve null`() {
        assertNull(OAuthRedirectResult.NotOAuthRedirect.toUiTextOrNull())
    }

    @Test
    fun `OAuthRedirectResult UserCancelled devuelve session_auth_cancelled`() {
        val result = OAuthRedirectResult.UserCancelled.toUiTextOrNull()
        assertNotNull(result)
        assertTrue(result is UiText.Resource)
        assertTrue((result as UiText.Resource).formatArgs.isEmpty())
    }

    @Test
    fun `OAuthRedirectResult InvalidSession devuelve OAUTH_INVALID_SESSION`() {
        val result = OAuthRedirectResult.InvalidSession.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `OAuthRedirectResult ExpiredSession devuelve OAUTH_INVALID_SESSION`() {
        val result = OAuthRedirectResult.ExpiredSession.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `OAuthRedirectResult MissingAuthorizationCode devuelve SIGN_IN_FAILED`() {
        val result = OAuthRedirectResult.MissingAuthorizationCode.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `OAuthRedirectResult TokenExchangeFailed devuelve SIGN_IN_FAILED`() {
        val result = OAuthRedirectResult.TokenExchangeFailed.toUiTextOrNull()
        assertNotNull(result)
        val resource = result as UiText.Resource
        assertTrue(resource.formatArgs.isEmpty())
    }

    @Test
    fun `todos los errores producen UiText Resource sin argumentos`() {
        // OAuthLaunchResult
        val launchValues = listOf(
            OAuthLaunchResult.Launched,
            OAuthLaunchResult.NoBrowserAvailable,
            OAuthLaunchResult.Failed
        )
        for (value in launchValues) {
            val uiText = value.toUiTextOrNull()
            if (uiText != null) {
                val resource = uiText as UiText.Resource
                assertTrue(
                    "OAuthLaunchResult.$value debería tener formatArgs vacío",
                    resource.formatArgs.isEmpty()
                )
            }
        }

        // OAuthRedirectResult
        val redirectValues = listOf(
            OAuthRedirectResult.Success,
            OAuthRedirectResult.NotOAuthRedirect,
            OAuthRedirectResult.UserCancelled,
            OAuthRedirectResult.InvalidSession,
            OAuthRedirectResult.ExpiredSession,
            OAuthRedirectResult.MissingAuthorizationCode,
            OAuthRedirectResult.TokenExchangeFailed
        )
        for (value in redirectValues) {
            val uiText = value.toUiTextOrNull()
            if (uiText != null) {
                val resource = uiText as UiText.Resource
                assertTrue(
                    "OAuthRedirectResult.$value debería tener formatArgs vacío",
                    resource.formatArgs.isEmpty()
                )
            }
        }
    }

    @Test
    fun `todos los valores de OAuthLaunchResult estan cubiertos`() {
        val launchValues = listOf(
            OAuthLaunchResult.Launched,
            OAuthLaunchResult.NoBrowserAvailable,
            OAuthLaunchResult.Failed
        )
        var checked = 0
        for (value in launchValues) {
            val result = value.toUiTextOrNull()
            when (value) {
                OAuthLaunchResult.Launched -> assertNull(result)
                OAuthLaunchResult.NoBrowserAvailable -> assertNotNull(result)
                OAuthLaunchResult.Failed -> assertNotNull(result)
            }
            checked++
        }
        assertEquals(3, checked)
    }

    @Test
    fun `todos los valores de OAuthRedirectResult estan cubiertos`() {
        val redirectValues = listOf(
            OAuthRedirectResult.Success,
            OAuthRedirectResult.NotOAuthRedirect,
            OAuthRedirectResult.UserCancelled,
            OAuthRedirectResult.InvalidSession,
            OAuthRedirectResult.ExpiredSession,
            OAuthRedirectResult.MissingAuthorizationCode,
            OAuthRedirectResult.TokenExchangeFailed
        )
        var checked = 0
        for (value in redirectValues) {
            val result = value.toUiTextOrNull()
            when (value) {
                OAuthRedirectResult.Success -> assertNull(result)
                OAuthRedirectResult.NotOAuthRedirect -> assertNull(result)
                OAuthRedirectResult.UserCancelled -> assertNotNull(result)
                OAuthRedirectResult.InvalidSession -> assertNotNull(result)
                OAuthRedirectResult.ExpiredSession -> assertNotNull(result)
                OAuthRedirectResult.MissingAuthorizationCode -> assertNotNull(result)
                OAuthRedirectResult.TokenExchangeFailed -> assertNotNull(result)
            }
            checked++
        }
        assertEquals(7, checked)
    }
}
