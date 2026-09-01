package com.david.mailapp.data.local.entity

import com.david.mailapp.data.local.converter.InlineContentReferenceCodec
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.EmailInlineReference
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailEntityContentContractsTest {

    @Test
    fun `EmailEntity round-trip mapping preserves NOT_FETCHED defaults`() {
        val domain = com.david.mailapp.feature.emaildetail.FakeEmailDetailSource.sampleEmail("1")
        assertEquals(EmailContentState.NOT_FETCHED, domain.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, domain.bodyKind)

        val entity = EmailEntity.fromDomain(domain, EmailFolder.Inbox)
        val restored = entity.toDomain()

        assertEquals(EmailContentState.NOT_FETCHED, restored.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, restored.bodyKind)
        assertEquals(0, restored.inlineReferences.size)
        assertEquals(0L, restored.cachedContentBytes)
    }

    @Test
    fun `EmailEntity round-trip mapping preserves READY state and references`() {
        val domain = com.david.mailapp.feature.emaildetail.FakeEmailDetailSource.sampleEmail("2").copy(
            contentState = EmailContentState.READY,
            bodyKind = EmailBodyKind.HTML,
            inlineReferences = listOf(EmailInlineReference("cid1", "att1", "image/png")),
            cachedContentBytes = 500L,
            contentLastAccessEpochMs = 12345L
        )

        val entity = EmailEntity.fromDomain(domain, EmailFolder.Inbox)
        val restored = entity.toDomain()

        assertEquals(EmailContentState.READY, restored.contentState)
        assertEquals(EmailBodyKind.HTML, restored.bodyKind)
        assertEquals("cid1", restored.inlineReferences[0].contentId)
        assertEquals(500L, restored.cachedContentBytes)
        assertEquals(12345L, restored.contentLastAccessEpochMs)
    }

    @Test
    fun `EmailEntity round-trip mapping preserves EMPTY state`() {
        val domain = com.david.mailapp.feature.emaildetail.FakeEmailDetailSource.sampleEmail("3").copy(
            contentState = EmailContentState.EMPTY,
            bodyKind = EmailBodyKind.UNKNOWN,
            cachedContentBytes = 0L
        )

        val entity = EmailEntity.fromDomain(domain, EmailFolder.Inbox)
        val restored = entity.toDomain()

        assertEquals(EmailContentState.EMPTY, restored.contentState)
        assertEquals(EmailBodyKind.UNKNOWN, restored.bodyKind)
    }

    @Test
    fun `InlineContentReferenceCodec handles empty and invalid JSON`() {
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode(""))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("null"))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("{ invalid }"))
        assertEquals(emptyList<EmailInlineReference>(), InlineContentReferenceCodec.decode("""[{"missing":"fields"}]"""))
    }
}
