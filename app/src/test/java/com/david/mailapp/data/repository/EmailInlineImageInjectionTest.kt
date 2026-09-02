package com.david.mailapp.data.repository

import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.local.entity.EmailSummaryProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailInlineImageInjectionTest {

    private class FakeDao : EmailDao {
        override fun observeSummariesByFolder(folder: String): Flow<List<EmailSummaryProjection>> = emptyFlow()
        override fun getById(emailId: String): Flow<EmailEntity?> = emptyFlow()
        override suspend fun getByIdOnce(emailId: String): EmailEntity? = null
        override suspend fun upsertAll(emails: List<EmailEntity>) {}
        override suspend fun moveToFolder(emailId: String, newFolder: String) {}
        override suspend fun updateReadStatus(emailId: String, isRead: Boolean) {}
        override suspend fun deleteById(emailId: String) {}
        override suspend fun clearFolder(folder: String) {}
        override suspend fun getEntitiesByFolderSync(folder: String): List<EmailEntity> = emptyList()
        override suspend fun getEntitiesByIdsSync(ids: List<String>): List<EmailEntity> = emptyList()
        override suspend fun updateBodyAndPdfMetadata(emailId: String, body: String, cleanBody: String, pdfAttachmentsJson: String, hasAttachments: Boolean, contentState: String, bodyKind: String, inlineReferencesJson: String, cachedContentBytes: Long) {}
        override suspend fun updateCleanBodyIfCurrent(emailId: String, expectedRawBody: String, cleanBody: String, cachedContentBytes: Long): Int = 1
        override suspend fun sumReadyContentBytes(): Long? = 0L
        override suspend fun getLruEvictionCandidates(protectedEmailId: String): List<EmailEntity> = emptyList()
        override suspend fun getGlobalLruEvictionCandidates(): List<EmailEntity> = emptyList()
        override suspend fun clearContent(emailId: String) {}
        override suspend fun getMaxContentLastAccess(): Long? = 0L
        override suspend fun updateContentLastAccess(emailId: String, newTimestamp: Long) {}
    }

    private fun createCoordinator(): EmailContentCoordinator {
        val dao = FakeDao()
        val guard = SessionWriteGuardImpl()
        val remoteRecovery = EmailRemoteRecoveryCoordinator(dao, { null }, guard)
        return EmailContentCoordinator(
            dao = dao,
            providerFactory = { null },
            remoteRecovery = remoteRecovery,
            writeGuard = guard
        )
    }

    @Test
    fun `replaces standard cid references`() {
        val coordinator = createCoordinator()
        val html = """<img src="cid:image_01">"""
        val images = mapOf("image_01" to "data:image/png;base64,AAA")
        val out = coordinator.injectInlineImages(html, images)
        assertEquals("""<img src="data:image/png;base64,AAA">""", out)
    }

    @Test
    fun `replaces angle brackets and html entity angle brackets`() {
        val coordinator = createCoordinator()
        val html1 = """<img src="cid:<image_02>">"""
        val html2 = """<img src="cid:&lt;image_02&gt;">"""
        val images = mapOf("image_02" to "data:image/png;base64,BBB")

        assertEquals("""<img src="data:image/png;base64,BBB">""", coordinator.injectInlineImages(html1, images))
        assertEquals("""<img src="data:image/png;base64,BBB">""", coordinator.injectInlineImages(html2, images))
    }

    @Test
    fun `case insensitive replacement of cid prefix and identifier`() {
        val coordinator = createCoordinator()
        val html = """<img src="CID:Image_03"><img src="cid:&LT;IMAGE_03&GT;">"""
        val images = mapOf("Image_03" to "data:image/png;base64,CCC")

        val out = coordinator.injectInlineImages(html, images)
        assertEquals("""<img src="data:image/png;base64,CCC"><img src="data:image/png;base64,CCC">""", out)
    }

    @Test
    fun `unknown cid remains intact and unrelated content untouched`() {
        val coordinator = createCoordinator()
        val html = """<p>Hello World</p><img src="cid:unknown_image">"""
        val images = mapOf("known_image" to "data:image/png;base64,DDD")

        val out = coordinator.injectInlineImages(html, images)
        assertEquals(html, out)
    }
}
