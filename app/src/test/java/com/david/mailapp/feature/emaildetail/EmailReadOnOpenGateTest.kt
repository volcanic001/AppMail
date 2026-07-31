package com.david.mailapp.feature.emaildetail

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailReadOnOpenGateTest {

    @Test
    fun unread_email_claims_one_mark_as_read_attempt() {
        val gate = EmailReadOnOpenGate()

        assertTrue(gate.claim(email(isRead = false)))
    }

    @Test
    fun repeated_room_emissions_do_not_claim_duplicate_attempts() {
        val gate = EmailReadOnOpenGate()
        val unreadEmail = email(isRead = false)

        assertTrue(gate.claim(unreadEmail))
        assertFalse(gate.claim(unreadEmail))
        assertFalse(gate.claim(unreadEmail))
    }

    @Test
    fun already_read_email_does_not_claim_an_attempt() {
        val gate = EmailReadOnOpenGate()

        assertFalse(gate.claim(email(isRead = true)))
    }

    private fun email(isRead: Boolean) = Email(
        id = "message-id",
        threadId = "thread-id",
        from = "sender@example.com",
        fromInitials = "S",
        to = "recipient@example.com",
        subject = "Read-on-open contract",
        snippet = "Contract fixture",
        timestamp = 1_000L,
        isRead = isRead,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox
    )
}
