package com.david.mailapp.feature.compose

import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeFormatUtilsSubphase4Test {

    private val fakeStringProvider = object : StringProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                com.david.mailapp.R.string.date_pattern_short -> "d MMM yyyy"
                com.david.mailapp.R.string.compose_reply_body_format -> "On ${formatArgs[0]}, ${formatArgs[1]} wrote:\n> ${formatArgs[2]}"
                com.david.mailapp.R.string.compose_forward_header -> "---------- Forwarded message ----------"
                com.david.mailapp.R.string.compose_forward_field_from -> "From: ${formatArgs[0]}"
                com.david.mailapp.R.string.compose_forward_field_date -> "Date: ${formatArgs[0]}"
                com.david.mailapp.R.string.compose_forward_field_subject -> "Subject: ${formatArgs[0]}"
                com.david.mailapp.R.string.compose_forward_field_to -> "To: ${formatArgs[0]}"
                else -> ""
            }
        }
    }

    private val formatUtils = ComposeFormatUtils(fakeStringProvider)

    private fun createEmail(
        body: String,
        cleanBody: String,
        bodyKind: EmailBodyKind
    ): Email = Email(
        id = "e1",
        threadId = "t1",
        from = "Alice <alice@example.com>",
        fromInitials = "A",
        to = "bob@example.com",
        subject = "Hello World",
        snippet = "Snippet text",
        timestamp = 1600000000000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = body,
        cleanBody = cleanBody,
        contentState = EmailContentState.READY,
        bodyKind = bodyKind
    )

    @Test
    fun `plain text body preserves exact literal tags ampersands spaces and newlines`() {
        val literalText = "Hello & <world>\n  Line 2 with <b>bold</b> text & spaces."
        val email = createEmail(
            body = literalText,
            cleanBody = "<p>Clean HTML version</p>",
            bodyKind = EmailBodyKind.PLAIN_TEXT
        )

        val result = ComposeFormatUtils.getOriginalPlainText(email)
        assertEquals(literalText, result)
    }

    @Test
    fun `reply and forward use plain text body directly even if cleanBody has different content`() {
        val literalText = "Plain text body with <tag> & spaces"
        val email = createEmail(
            body = literalText,
            cleanBody = "<html><body>Clean body</body></html>",
            bodyKind = EmailBodyKind.PLAIN_TEXT
        )

        val replyBody = formatUtils.buildReplyBody("My reply", email, "")
        assertTrue(replyBody.contains("> Plain text body with <tag> & spaces"))

        val forwardBody = formatUtils.buildForwardBody("My forward", email, "")
        assertTrue(forwardBody.contains("Plain text body with <tag> & spaces"))
    }

    @Test
    fun `html body uses cleanBody and performs jsoup conversion`() {
        val email = createEmail(
            body = "<html><body><h1>Title</h1><p>Paragraph &amp; text.</p></body></html>",
            cleanBody = "<h1>Title</h1><p>Paragraph &amp; text.</p>",
            bodyKind = EmailBodyKind.HTML
        )

        val result = ComposeFormatUtils.getOriginalPlainText(email)
        assertEquals("Title\nParagraph & text.", result)
    }
}
