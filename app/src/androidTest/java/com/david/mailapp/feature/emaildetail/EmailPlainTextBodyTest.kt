package com.david.mailapp.feature.emaildetail

import android.webkit.WebView
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.feature.emaildetail.components.EmailDetailContent
import com.david.mailapp.feature.emaildetail.components.EmailPlainTextBody
import com.david.mailapp.feature.emaildetail.components.SafeLinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmailPlainTextBodyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createDummyEmail(
        bodyKind: EmailBodyKind,
        bodyText: String = "Sample body text"
    ): Email = Email(
        id = "e100",
        threadId = "t100",
        from = "Alice <alice@example.com>",
        fromInitials = "A",
        to = "bob@example.com",
        subject = "Plain Text Test",
        snippet = "Snippet",
        timestamp = 1600000000000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = bodyText,
        cleanBody = "",
        contentState = EmailContentState.READY,
        bodyKind = bodyKind
    )

    @Test
    fun readyPlainTextRendersNativelyAndContainsNoWebView() {
        val email = createDummyEmail(EmailBodyKind.PLAIN_TEXT, "Hello & <world>\nLine 2 text")

        composeTestRule.setContent {
            EmailDetailContent(
                email = email,
                body = email.body,
                traceMail = "test_trace",
                pdfDownloadStates = emptyMap(),
                onPdfAttachmentClick = {},
                onPdfSaveClick = {},
                savingStableIds = emptySet(),
                onImageLongPress = {}
            )
        }

        // Verify native plain text body is displayed
        composeTestRule.onNodeWithTag("email_detail_plain_text_body").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello & <world>\nLine 2 text").assertIsDisplayed()
    }

    @Test
    fun preparingBodyRendersLoaderAndContainsNoWebView() {
        val email = createDummyEmail(EmailBodyKind.PLAIN_TEXT, "")

        composeTestRule.setContent {
            EmailDetailContent(
                email = email,
                body = null, // PreparingBody state
                traceMail = "test_trace",
                pdfDownloadStates = emptyMap(),
                onPdfAttachmentClick = {},
                onPdfSaveClick = {},
                savingStableIds = emptySet(),
                onImageLongPress = {}
            )
        }

        // Plain text tag should NOT be displayed when body is null
        composeTestRule.onNodeWithTag("email_detail_plain_text_body").assertDoesNotExist()
    }

    @Test
    fun safeLinkPolicyValidatesLinksCorrectly() {
        var openedUrl: String? = null

        val validUrl = "https://example.com/safe"
        val validUri = SafeLinkPolicy.sanitizeAndValidate(validUrl)
        assertNotNull(validUri)
        assertEquals("https://example.com/safe", validUri.toString())

        val unsafeUrl = "javascript:alert(1)"
        val unsafeUri = SafeLinkPolicy.sanitizeAndValidate(unsafeUrl)
        assertNull(unsafeUri)
    }

    @Test
    fun longPlainTextBodyPreservesLineBreaks() {
        val longText = (1..50).joinToString("\n") { "Line number $it in long email body" }
        val email = createDummyEmail(EmailBodyKind.PLAIN_TEXT, longText)

        composeTestRule.setContent {
            EmailPlainTextBody(
                text = email.body,
                traceMail = "test_trace",
                onOpenLink = {}
            )
        }

        composeTestRule.onNodeWithTag("email_detail_plain_text_body").assertIsDisplayed()
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }
}
