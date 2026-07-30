package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.gmail.GmailProvider
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class PartialPageContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var client: HttpClient
    private lateinit var repository: EmailRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users/me/messages") -> respond(
                    content = """{
                        "messages": [
                            {"id":"detail-ok","threadId":"thread-ok"},
                            {"id":"detail-fails","threadId":"thread-fails"}
                        ],
                        "nextPageToken":"remote-next"
                    }""".trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                request.url.encodedPath.endsWith("/messages/detail-ok") -> respond(
                    content = """{
                        "id":"detail-ok",
                        "threadId":"thread-ok",
                        "labelIds":["INBOX"],
                        "snippet":"Fetched detail",
                        "internalDate":"2000",
                        "payload":{"headers":[
                            {"name":"From","value":"sender@test.com"},
                            {"name":"To","value":"me@test.com"},
                            {"name":"Subject","value":"Complete detail"}
                        ]}
                    }""".trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                request.url.encodedPath.endsWith("/messages/detail-fails") -> respond(
                    content = """{"error":"detail failed"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = jsonHeaders
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }
        client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val cacheDir = java.io.File(context.cacheDir, "pdf_test_${System.nanoTime()}").apply { mkdirs() }
        repository = EmailRepository(
            db,
            { GmailProvider(client) },
            PdfCacheManager(cacheDir),
            FakeSessionWriteGuard()
        )
    }

    @After
    fun tearDown() {
        client.close()
        db.close()
    }

    // Activated by Fase 2.2: modelar respuestas parciales
    @Test
    fun c6_partial_page_does_not_replace_cache_or_advance_token() = runTest {
        val existing = (1..5).map { testEmail("existing-$it", subject = "Cached $it") }
        db.emailDao().upsertAll(existing.map { EmailEntity.fromDomain(it, EmailFolder.Inbox) })

        val result = repository.refreshInbox(null)

        assertEquals("Only one of two listed details was recovered", 1, result.items.size)
        assertNull("An incomplete Gmail page must not expose the remote token", result.nextPageToken)
        val cached = db.emailDao().getEntitiesByFolderSync("inbox")
        assertTrue("Existing cache must survive a partial page", cached.any { it.id == "existing-1" })
        assertTrue("The recovered detail may be merged", cached.any { it.id == "detail-ok" })
        assertEquals(6, cached.size)
    }
}
