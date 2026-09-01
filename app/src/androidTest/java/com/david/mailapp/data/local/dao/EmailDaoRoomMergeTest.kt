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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailDaoRoomMergeTest {

    private lateinit var textDb: MailDatabase
    private lateinit var emailDao: EmailDao

    @Before
    fun setup() {
        textDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MailDatabase::class.java
        ).allowMainThreadQueries().build()
        emailDao = textDb.emailDao()
    }

    @After
    fun teardown() {
        textDb.close()
    }

    private fun entity(
        id: String = "msg_1",
        contentState: String = "NOT_FETCHED",
        body: String = "",
        cleanBody: String = "",
        pdfJson: String = "[]",
        pdfScanned: Boolean = false,
        hasAtt: Boolean = false
    ): EmailEntity = EmailEntity(
        id = id, threadId = "t1", from = "a", fromInitials = "A",
        to = "b", subject = "s", snippet = "s", timestamp = 1L,
        isRead = true, isStarred = false, hasAttachments = hasAtt,
        labels = "", folder = "inbox",
        body = body, cleanBody = cleanBody,
        contentState = contentState, bodyKind = "UNKNOWN",
        inlineReferencesJson = "[]", cachedContentBytes = 0L,
        contentLastAccessEpochMs = 0L,
        pdfAttachmentsJson = pdfJson, pdfMetadataScanned = pdfScanned,
        rfcMessageId = null, rfcReferences = null
    )

    @Test
    fun replaceFolder_mergesExistingEntities() = runTest {
        // 1. Insert existing READY email
        emailDao.upsertWithMerge(entity(id = "msg_1", contentState = "READY", body = "r1"))
        emailDao.upsertWithMerge(entity(id = "msg_2", contentState = "EMPTY", body = ""))

        // 2. Incoming folder replace with NOT_FETCHED
        val incoming = listOf(
            entity(id = "msg_1", contentState = "NOT_FETCHED", body = ""),
            entity(id = "msg_2", contentState = "NOT_FETCHED", body = ""),
            entity(id = "msg_3", contentState = "NOT_FETCHED", body = "")
        )
        emailDao.replaceFolder("inbox", incoming)

        // 3. Verify
        val e1 = emailDao.getById("msg_1").first()!!
        assertEquals("READY", e1.contentState)
        assertEquals("r1", e1.body)

        val e2 = emailDao.getById("msg_2").first()!!
        assertEquals("EMPTY", e2.contentState)

        val e3 = emailDao.getById("msg_3").first()!!
        assertEquals("NOT_FETCHED", e3.contentState)
    }

    @Test
    fun upsertPreservingCachedContent_preservesReady() = runTest {
        emailDao.upsertWithMerge(entity(id = "msg_1", contentState = "READY", body = "r1"))

        emailDao.upsertPreservingCachedContent(listOf(
            entity(id = "msg_1", contentState = "NOT_FETCHED")
        ))

        val e1 = emailDao.getById("msg_1").first()!!
        assertEquals("READY", e1.contentState)
        assertEquals("r1", e1.body)
    }

    @Test
    fun upsertWithMerge_appliesIncomingAuthoritative() = runTest {
        emailDao.upsertWithMerge(entity(id = "msg_1", contentState = "NOT_FETCHED"))

        emailDao.upsertWithMerge(entity(id = "msg_1", contentState = "READY", body = "r1"))

        val e1 = emailDao.getById("msg_1").first()!!
        assertEquals("READY", e1.contentState)
        assertEquals("r1", e1.body)
    }
}
