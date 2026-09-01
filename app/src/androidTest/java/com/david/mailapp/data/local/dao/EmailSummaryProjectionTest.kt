package com.david.mailapp.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.local.entity.EmailSummaryProjection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

        val otherEmail = EmailEntity(
            id = "msg4",
            threadId = "thread4",
            from = "other@test.com",
            fromInitials = "O",
            to = "me@test.com",
            subject = "Other Subject",
            snippet = "Other",
            timestamp = 1800L,
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            labels = "",
            folder = "other",
            body = "Other body",
            cleanBody = "Other body",
            pdfAttachmentsJson = "[]",
            pdfMetadataScanned = false,
            rfcMessageId = null,
            rfcReferences = null
        )

        dao.upsertAll(listOf(inboxEmail1, inboxEmail2, trashEmail, otherEmail))

        val inboxSummaries = dao.observeSummariesByFolder("inbox").first()
        val trashSummaries = dao.observeSummariesByFolder("trash").first()
        val otherSummaries = dao.observeSummariesByFolder("other").first()

        // Verifies folder isolation and descending order by timestamp
        assertEquals(2, inboxSummaries.size)
        assertEquals("msg2", inboxSummaries[0].id)
        assertEquals("msg1", inboxSummaries[1].id)

        assertEquals(1, trashSummaries.size)
        assertEquals("msg3", trashSummaries[0].id)

        assertEquals(1, otherSummaries.size)
        assertEquals("msg4", otherSummaries[0].id)

        // Verify otherEmail does not appear in inbox or trash lists
        assertEquals(false, inboxSummaries.any { it.id == "msg4" })
        assertEquals(false, trashSummaries.any { it.id == "msg4" })

        // Verifies mapping of all 13 fields (checking second item 'msg1' which has true flags)
        val second = inboxSummaries[1]
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
    }

    @Test
    fun projectionContainsExactlyThirteenAllowedFields() {
        // Reflection check to ensure the exact set of properties matches the 13 allowed fields.
        // This validates simultaneously that no heavy fields exist and no required fields are missing.
        val allowedFields = setOf(
            "id", "threadId", "from", "fromInitials", "to", "subject", "snippet",
            "timestamp", "isRead", "isStarred", "hasAttachments", "labels", "folder"
        )

        val actualFields = EmailSummaryProjection::class.java.declaredFields
            .filter { !it.isSynthetic && !it.name.startsWith("\$") } // Filter out compiler/jacoco/compose synthetic fields
            .map { it.name }
            .toSet()

        assertEquals(allowedFields, actualFields)
    }
}
