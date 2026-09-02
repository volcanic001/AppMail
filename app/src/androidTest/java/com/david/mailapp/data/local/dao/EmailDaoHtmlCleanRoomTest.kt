package com.david.mailapp.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailDaoHtmlCleanRoomTest {

    private lateinit var database: MailDatabase
    private lateinit var emailDao: EmailDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MailDatabase::class.java
        ).allowMainThreadQueries().build()
        emailDao = database.emailDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createEntity(
        id: String = "e101",
        body: String = "<p>Uncleaned body</p>",
        cleanBody: String = "",
        contentState: String = "READY",
        bodyKind: String = "HTML",
        cachedContentBytes: Long = 100L
    ) = EmailEntity(
        id = id,
        threadId = "t101",
        from = "alice@example.com",
        fromInitials = "A",
        to = "bob@example.com",
        subject = "Room HTML Clean Test",
        snippet = "Snippet",
        timestamp = 1000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = "[]",
        folder = "INBOX",
        body = body,
        cleanBody = cleanBody,
        pdfAttachmentsJson = "[]",
        pdfMetadataScanned = false,
        rfcMessageId = null,
        rfcReferences = null,
        contentState = contentState,
        bodyKind = bodyKind,
        inlineReferencesJson = "[]",
        cachedContentBytes = cachedContentBytes,
        contentLastAccessEpochMs = 1000L
    )

    @Test
    fun casUpdateSucceedsWhenCleanBodyIsEmptyAndBodyMatches() = runTest {
        val entity = createEntity(cleanBody = "")
        emailDao.upsertAll(listOf(entity))

        val cleanResult = "<div style=\"margin:0 16px;\"><p>Uncleaned body</p></div>"
        val updatedBytes = 250L

        val success = emailDao.updateCleanBodyIfCurrentAndEnforceLru(
            emailId = "e101",
            expectedRawBody = "<p>Uncleaned body</p>",
            cleanBody = cleanResult,
            cachedContentBytes = updatedBytes
        )

        assertTrue(success)
        val loaded = emailDao.getByIdOnce("e101")
        assertEquals(cleanResult, loaded?.cleanBody)
        assertEquals(updatedBytes, loaded?.cachedContentBytes)
    }

    @Test
    fun casUpdateFailsWhenCleanBodyIsAlreadyPopulated() = runTest {
        val entity = createEntity(cleanBody = "<p>Already cleaned</p>")
        emailDao.upsertAll(listOf(entity))

        val success = emailDao.updateCleanBodyIfCurrentAndEnforceLru(
            emailId = "e101",
            expectedRawBody = "<p>Uncleaned body</p>",
            cleanBody = "<p>New clean</p>",
            cachedContentBytes = 300L
        )

        assertFalse(success)
        val loaded = emailDao.getByIdOnce("e101")
        assertEquals("<p>Already cleaned</p>", loaded?.cleanBody)
    }

    @Test
    fun syncMergePreservesCleanBodyOnlyWhenBodyMatches() = runTest {
        val existing = createEntity(body = "<p>Body 1</p>", cleanBody = "<p>Clean 1</p>")
        emailDao.upsertAll(listOf(existing))

        // 1. Incoming with same body -> preserves cleanBody
        val incomingSame = createEntity(body = "<p>Body 1</p>", cleanBody = "")
        val mergedSame = mergeWithExisting(incomingSame, existing)
        assertEquals("<p>Clean 1</p>", mergedSame.cleanBody)

        // 2. Incoming with different body -> resets cleanBody to empty
        val incomingDifferent = createEntity(body = "<p>Body 2</p>", cleanBody = "")
        val mergedDifferent = mergeWithExisting(incomingDifferent, existing)
        assertEquals("", mergedDifferent.cleanBody)
    }
}
