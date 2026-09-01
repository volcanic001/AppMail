package com.david.mailapp.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailSummaryProjectionTest {

    private lateinit var database: MailDatabase
    private lateinit var dao: EmailDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MailDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.emailDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun observeSummariesByFolder_returnsOnlyLightweightFieldsAndIsolatesFolders() = runTest {
        val largeHtmlBody = "<html><body>" + "A".repeat(5000) + "</body></html>"
        val largeCleanBody = "A".repeat(5000)
        
        val inboxEmail1 = EmailEntity(
            id = "msg1",
            threadId = "thread1",
            from = "sender@test.com",
            fromInitials = "S",
            to = "me@test.com",
            subject = "Inbox Subject 1",
            snippet = "Snippet 1",
            timestamp = 1000L,
            isRead = false,
            isStarred = true,
            hasAttachments = true,
            labels = "IMPORTANT",
            folder = "inbox",
            body = largeHtmlBody,
            cleanBody = largeCleanBody,
            pdfAttachmentsJson = "[{\"id\":\"att1\",\"name\":\"file.pdf\",\"sizeBytes\":1000}]",
            pdfMetadataScanned = true,
            rfcMessageId = "<msg1@test.com>",
            rfcReferences = "<ref1@test.com>"
        )

        val inboxEmail2 = EmailEntity(
            id = "msg2",
            threadId = "thread2",
            from = "sender2@test.com",
            fromInitials = "S2",
            to = "me@test.com",
            subject = "Inbox Subject 2",
            snippet = "Snippet 2",
            timestamp = 2000L, // Newer
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            labels = "",
            folder = "inbox",
            body = largeHtmlBody,
            cleanBody = largeCleanBody,
            pdfAttachmentsJson = "[]",
            pdfMetadataScanned = true,
            rfcMessageId = "<msg2@test.com>",
            rfcReferences = null
        )

        val trashEmail = EmailEntity(
            id = "msg3",
            threadId = "thread3",
            from = "spammer@test.com",
            fromInitials = "SP",
            to = "me@test.com",
            subject = "Trash Subject",
            snippet = "Spam",
            timestamp = 1500L,
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            labels = "SPAM",
            folder = "trash",
            body = "Buy now",
            cleanBody = "Buy now",
            pdfAttachmentsJson = "[]",
            pdfMetadataScanned = true,
            rfcMessageId = "<msg3@test.com>",
            rfcReferences = null
        )

        dao.upsertAll(listOf(inboxEmail1, inboxEmail2, trashEmail))

        val inboxSummaries = dao.observeSummariesByFolder("inbox").first()
        
        // Verifies folder isolation
        assertEquals(2, inboxSummaries.size)

        // Verifies descending order by timestamp
        val first = inboxSummaries[0]
        val second = inboxSummaries[1]
        assertEquals("msg2", first.id)
        assertEquals(2000L, first.timestamp)
        assertEquals("msg1", second.id)
        assertEquals(1000L, second.timestamp)

        // Verifies mapping of all 13 fields (checking second item 'msg1' which has true flags)
        assertEquals("msg1", second.id)
        assertEquals("thread1", second.threadId)
        assertEquals("sender@test.com", second.from)
        assertEquals("S", second.fromInitials)
        assertEquals("me@test.com", second.to)
        assertEquals("Inbox Subject 1", second.subject)
        assertEquals("Snippet 1", second.snippet)
        assertEquals(1000L, second.timestamp)
        assertEquals(false, second.isRead)
        assertEquals(true, second.isStarred)
        assertEquals(true, second.hasAttachments)
        assertEquals("IMPORTANT", second.labels)
        assertEquals("inbox", second.folder)

        // Compile-time check: First and second shouldn't have any body, cleanBody, pdfAttachmentsJson properties
        // We ensure structurally the fields don't exist by verifying the projection doesn't declare them.
        val declaredFields = com.david.mailapp.data.local.entity.EmailSummaryProjection::class.java.declaredFields.map { it.name }
        assertTrue(declaredFields.contains("id"))
        assertTrue(!declaredFields.contains("body"))
        assertTrue(!declaredFields.contains("cleanBody"))
        assertTrue(!declaredFields.contains("pdfAttachmentsJson"))
        assertTrue(!declaredFields.contains("pdfMetadataScanned"))
        assertTrue(!declaredFields.contains("rfcMessageId"))
        assertTrue(!declaredFields.contains("rfcReferences"))
    }
}
