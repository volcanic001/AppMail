package com.david.mailapp.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailFolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailDaoLruTest {

    private lateinit var db: MailDatabase
    private lateinit var dao: EmailDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MailDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.emailDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun insertEmail(
        id: String,
        body: String = "",
        pdfJson: String = "[]",
        bytes: Long = 0L,
        accessEpoch: Long = 0L
    ) {
        val entity = EmailEntity(
            id = id,
            threadId = "t1",
            from = "a@b.com",
            fromInitials = "A",
            to = "c@d.com",
            subject = "S",
            snippet = "s",
            timestamp = 1L,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            labels = "[]",
            folder = EmailFolder.Inbox.name,
            body = body,
            cleanBody = body,
            pdfAttachmentsJson = pdfJson,
            pdfMetadataScanned = true,
            inlineReferencesJson = "[]",
            contentState = EmailContentState.READY.name,
            bodyKind = EmailBodyKind.HTML.name,
            cachedContentBytes = bytes,
            contentLastAccessEpochMs = accessEpoch
        )
        dao.upsertAll(listOf(entity))
    }

    @Test
    fun getLruEvictionCandidates_ordersByAccessAndId() = runTest {
        insertEmail("e1", bytes = 1000L, accessEpoch = 100L)
        insertEmail("e2", bytes = 2000L, accessEpoch = 50L)
        insertEmail("e3", bytes = 500L, accessEpoch = 200L)
        insertEmail("e4", bytes = 3000L, accessEpoch = 50L)

        val candidates = dao.getLruEvictionCandidates("e3")
        assertEquals(3, candidates.size)
        assertEquals("e2", candidates[0].id)
        assertEquals("e4", candidates[1].id)
        assertEquals("e1", candidates[2].id)
    }

    @Test
    fun clearContent_resetsContentFields_preservesPdfs() = runTest {
        insertEmail("e1", body = "<html>heavy</html>", pdfJson = "[{\"id\":\"p1\"}]", bytes = 5000L)

        dao.clearContent("e1")

        val updated = dao.getById("e1").first()!!
        assertEquals("", updated.body)
        assertEquals(EmailContentState.NOT_FETCHED.name, updated.contentState)
        assertEquals(0L, updated.cachedContentBytes)
        assertEquals("[{\"id\":\"p1\"}]", updated.pdfAttachmentsJson)
    }

    @Test
    fun applyLruAndSaveContent_evictsWhenOverBudget() = runTest {
        insertEmail("e1", bytes = 30_000_000L, accessEpoch = 10L)
        insertEmail("e2", bytes = 20_000_000L, accessEpoch = 20L)
        insertEmail("e3", bytes = 0L, accessEpoch = 30L)

        dao.applyLruAndSaveContent(
            emailId = "e3",
            body = "new body",
            cleanBody = "new clean body",
            pdfAttachmentsJson = "[]",
            hasAttachments = false,
            contentState = EmailContentState.READY.name,
            bodyKind = EmailBodyKind.HTML.name,
            inlineReferencesJson = "[]",
            cachedContentBytes = 10_000_000L,
            maxBudgetBytes = 52_428_800L
        )

        val e1Updated = dao.getById("e1").first()!!
        assertEquals(EmailContentState.NOT_FETCHED.name, e1Updated.contentState)
        assertEquals(0L, e1Updated.cachedContentBytes)

        val e2Updated = dao.getById("e2").first()!!
        assertEquals(EmailContentState.READY.name, e2Updated.contentState)
        assertEquals(20_000_000L, e2Updated.cachedContentBytes)

        val e3Updated = dao.getById("e3").first()!!
        assertEquals(EmailContentState.READY.name, e3Updated.contentState)
        assertEquals(10_000_000L, e3Updated.cachedContentBytes)
    }

    @Test
    fun enforceContentBudget_evictsGloballyByAccessThenId() = runTest {
        insertEmail("b", bytes = 20_000_000L, accessEpoch = 0L)
        insertEmail("a", bytes = 20_000_000L, accessEpoch = 0L)
        insertEmail("recent", bytes = 20_000_000L, accessEpoch = 10L)

        dao.enforceContentBudget(52_428_800L)

        assertEquals(EmailContentState.NOT_FETCHED.name, dao.getByIdOnce("a")!!.contentState)
        assertEquals(EmailContentState.READY.name, dao.getByIdOnce("b")!!.contentState)
        assertEquals(EmailContentState.READY.name, dao.getByIdOnce("recent")!!.contentState)
        assertEquals(40_000_000L, dao.sumReadyContentBytes())
    }
}
