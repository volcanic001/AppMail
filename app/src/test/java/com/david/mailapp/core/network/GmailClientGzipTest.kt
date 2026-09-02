package com.david.mailapp.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.auth.OAuthTokenManager
import com.david.mailapp.core.auth.OAuthTokenResult
import com.david.mailapp.core.auth.OAuthTokens
import com.david.mailapp.core.security.SecretCipher
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class GmailClientGzipTest {

    private fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray()) }
        return bos.toByteArray()
    }

    private class FakeSecretCipher : SecretCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): String = 
            java.util.Base64.getEncoder().encodeToString(plaintext)
        override fun decrypt(encrypted: String, aad: ByteArray): ByteArray = 
            java.util.Base64.getDecoder().decode(encrypted)
    }

    private class FakeRefreshService : com.david.mailapp.core.auth.OAuthRefreshService {
        override suspend fun refresh(refreshToken: String): com.david.mailapp.core.auth.OAuthRefreshResult {
            return com.david.mailapp.core.auth.OAuthRefreshResult.Success("newAccess", 3600)
        }
    }

    private fun createTokenManager(): OAuthTokenManager {
        val store = PreferenceDataStoreFactory.create {
            java.io.File.createTempFile("test_prefs", ".preferences_pb").apply { deleteOnExit() }
        }
        val authManager = AuthManager(store, FakeSecretCipher())
        kotlinx.coroutines.runBlocking {
            authManager.saveTokens(OAuthTokens("test_access", "test_refresh", System.currentTimeMillis() + 3600_000))
        }
        return OAuthTokenManager(authManager, FakeRefreshService(), nowEpochMillis = { System.currentTimeMillis() })
    }

    @Test
    fun `gmail client sends Accept-Encoding and User-Agent gzip and decompresses response`() = runTest {
        var acceptEncoding: String? = null
        var userAgent: String? = null
        
        val originalJson = """{"success":true,"message":"hello world"}"""
        val compressed = compress(originalJson)

        val engine = MockEngine { request ->
            acceptEncoding = request.headers[HttpHeaders.AcceptEncoding]
            userAgent = request.headers[HttpHeaders.UserAgent]
            
            respond(
                compressed, 
                HttpStatusCode.OK, 
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ContentEncoding to listOf("gzip")
                )
            )
        }

        val client = HttpClientFactory.createGmailClient(createTokenManager(), engine)
        
        val responseText = client.get("https://gmail.googleapis.com/gmail/v1/test").bodyAsText()
        
        assertTrue("Accept-Encoding should contain gzip", acceptEncoding?.contains("gzip") == true)
        assertTrue("User-Agent should contain gzip", userAgent?.contains("gzip") == true)
        assertEquals(originalJson, responseText)
    }

    @Test
    fun `gmail client works with uncompressed response`() = runTest {
        val originalJson = """{"success":true,"message":"uncompressed"}"""
        
        val engine = MockEngine { request ->
            respond(
                originalJson, 
                HttpStatusCode.OK, 
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClientFactory.createGmailClient(createTokenManager(), engine)
        
        val responseText = client.get("https://gmail.googleapis.com/gmail/v1/test").bodyAsText()
        
        assertEquals(originalJson, responseText)
    }
}
