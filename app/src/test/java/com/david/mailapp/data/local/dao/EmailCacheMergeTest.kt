package com.david.mailapp.data.local.dao

import com.david.mailapp.data.local.entity.EmailEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailCacheMergeTest {

    private fun entity(
        id: String = "msg_1",
        body: String = "",
        cleanBody: String = "",
        contentState: String = "NOT_FETCHED",
        bodyKind: String = "UNKNOWN",
        inlineRefs: String = "[]",
        cachedBytes: Long = 0L,
        lastAccess: Long = 0L,
        pdfJson: String = "[]",
        pdfScanned: Boolean = false,
        hasAtt: Boolean = false,
        rfcMessageId: String? = null,
        rfcReferences: String? = null
    ): EmailEntity = EmailEntity(
        id = id, threadId = "t1", from = "a", fromInitials = "A",
        to = "b", subject = "s", snippet = "s", timestamp = 1L,
        isRead = true, isStarred = false, hasAttachments = hasAtt,
        labels = "", folder = "inbox",
        body = body, cleanBody = cleanBody,
        contentState = contentState, bodyKind = bodyKind,
        inlineReferencesJson = inlineRefs, cachedContentBytes = cachedBytes,
        contentLastAccessEpochMs = lastAccess,
        pdfAttachmentsJson = pdfJson, pdfMetadataScanned = pdfScanned,
        rfcMessageId = rfcMessageId, rfcReferences = rfcReferences
    )

    @Test
    fun `READY vs NOT_FETCHED preserves entire READY unit`() {
        val existing = entity(contentState = "READY", body = "R", cleanBody = "r", bodyKind = "HTML", inlineRefs = "[1]", cachedBytes = 10, lastAccess = 100)
        val incoming = entity(contentState = "NOT_FETCHED", body = "", cleanBody = "", bodyKind = "UNKNOWN", inlineRefs = "[]", cachedBytes = 0, lastAccess = 0)

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("READY", merged.contentState)
        assertEquals("R", merged.body)
        assertEquals("r", merged.cleanBody)
        assertEquals("HTML", merged.bodyKind)
        assertEquals("[1]", merged.inlineReferencesJson)
        assertEquals(10L, merged.cachedContentBytes)
        assertEquals(100L, merged.contentLastAccessEpochMs)
    }

    @Test
    fun `EMPTY vs NOT_FETCHED preserves entire EMPTY unit`() {
        val existing = entity(contentState = "EMPTY", body = "", cleanBody = "", bodyKind = "UNKNOWN", inlineRefs = "[]", cachedBytes = 0, lastAccess = 0)
        val incoming = entity(contentState = "NOT_FETCHED", body = "", cleanBody = "", bodyKind = "UNKNOWN", inlineRefs = "[]", cachedBytes = 0, lastAccess = 0)

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("EMPTY", merged.contentState)
    }

    @Test
    fun `NOT_FETCHED vs NOT_FETCHED normalizes NOT_FETCHED`() {
        val existing = entity(contentState = "NOT_FETCHED")
        val incoming = entity(contentState = "NOT_FETCHED", body = "some_error", lastAccess = 999)

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("NOT_FETCHED", merged.contentState)
        assertEquals("some_error", merged.body)
        assertEquals(0L, merged.contentLastAccessEpochMs) // Access is zeroed if not READY
    }

    @Test
    fun `ANY vs READY performs authoritative replacement`() {
        val existing = entity(contentState = "EMPTY", lastAccess = 50)
        val incoming = entity(contentState = "READY", body = "N", lastAccess = 200)

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("READY", merged.contentState)
        assertEquals("N", merged.body)
        assertEquals(200L, merged.contentLastAccessEpochMs) // Because existing wasn't READY, we just take incoming's (or 0, but incoming was READY so 200)
    }

    @Test
    fun `ANY vs EMPTY cleans content`() {
        val existing = entity(contentState = "READY", body = "old", lastAccess = 100)
        val incoming = entity(contentState = "EMPTY", body = "", lastAccess = 200)

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("EMPTY", merged.contentState)
        assertEquals("", merged.body)
        assertEquals(0L, merged.contentLastAccessEpochMs) // NOT READY -> 0
    }

    @Test
    fun `READY vs READY replaces content but preserves existing access`() {
        val existing = entity(contentState = "READY", body = "old", cachedBytes = 5, lastAccess = 500)
        val incoming = entity(contentState = "READY", body = "new", cachedBytes = 10, lastAccess = 100) // maybe incoming has weird access

        val merged = mergeWithExisting(incoming, existing)

        assertEquals("READY", merged.contentState)
        assertEquals("new", merged.body)
        assertEquals(10L, merged.cachedContentBytes)
        assertEquals(500L, merged.contentLastAccessEpochMs) // Existing recency preserved
    }

    @Test
    fun `PDF rules`() {
        val existing = entity(pdfScanned = true, hasAtt = true, pdfJson = "[old]")
        val incoming = entity(pdfScanned = false, hasAtt = false, pdfJson = "[]")

        val merged = mergeWithExisting(incoming, existing)
        assertTrue(merged.pdfMetadataScanned)
        assertTrue(merged.hasAttachments)
        assertEquals("[old]", merged.pdfAttachmentsJson)
    }

    @Test
    fun `RFC rules`() {
        val existing = entity(rfcMessageId = "a", rfcReferences = "b")
        val incoming = entity(rfcMessageId = null, rfcReferences = null)
        val merged = mergeWithExisting(incoming, existing)
        assertEquals("a", merged.rfcMessageId)
        assertEquals("b", merged.rfcReferences)
    }
}
