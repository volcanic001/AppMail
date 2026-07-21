package com.david.mailapp.core.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [GoogleOAuthTokenService] (Fase 1C.1).
 *
 * Uses ktor-client-mock ([MockEngine]) to simulate HTTP responses
 * without a real network.
 */
class GoogleOAuthTokenServiceTest {

    @Test
    fun `HTTP 200 with valid token returns Success`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"new_token","expires_in":3600,"token_type":"Bearer"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertTrue("200 must be Success", result is OAuthRefreshResult.Success)
        val success = result as OAuthRefreshResult.Success
        assertEquals("new_token", success.accessToken)
        assertEquals(3600, success.expiresInSeconds)
    }

    @Test
    fun `HTTP 200 with empty access_token returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"","expires_in":3600}"""),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("Empty access_token must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `HTTP 200 with expires_in=0 returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"new_token","expires_in":0}"""),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("expires_in=0 must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `HTTP 400 with invalid_grant returns ReauthenticationRequired`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"invalid_grant","error_description":"Token has been revoked"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("revoked_refresh_token")
        assertEquals("invalid_grant must be ReauthenticationRequired", OAuthRefreshResult.ReauthenticationRequired, result)
    }

    @Test
    fun `HTTP 400 without invalid_grant returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"invalid_request"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("Other 400 must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `HTTP 500 returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("Internal Server Error"),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf("Content-Type", "text/plain")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("500 must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `HTTP 429 returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("Too Many Requests"),
                status = HttpStatusCode(429, "Too Many Requests"),
                headers = headersOf("Content-Type", "text/plain")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("429 must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `invalid JSON response returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("this is not valid json"),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("Invalid JSON must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }

    @Test
    fun `engine exception returns TransientFailure`() = runBlocking {
        val engine = MockEngine { _ ->
            throw java.io.IOException("Simulated network error")
        }
        val service = GoogleOAuthTokenService(client = io.ktor.client.HttpClient(engine))
        val result = service.refresh("any_refresh_token")
        assertEquals("IOException must be TransientFailure", OAuthRefreshResult.TransientFailure, result)
    }
}
