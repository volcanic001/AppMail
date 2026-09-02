package com.david.mailapp.feature.emaildetail.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.EmailInlineReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailBodyWebViewProgressiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createEmail(
        id: String = "e30",
        cleanBody: String = "<p>Text Content</p><img src=\"cid:img1\">",
        refs: List<EmailInlineReference> = listOf(EmailInlineReference("img1", "att1", "image/png"))
    ) = Email(
        id = id,
        threadId = "t30",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Progressive UI Test",
        snippet = "Snippet",
        timestamp = 100000L,
        isRead = true,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox,
        body = cleanBody,
        cleanBody = cleanBody,
        contentState = EmailContentState.READY,
        bodyKind = EmailBodyKind.HTML,
        inlineReferences = refs
    )

    @Test
    fun textualContentRendersInWebViewWithProgressiveCidUpdate() {
        val email = createEmail()

        composeTestRule.setContent {
            EmailDetailContent(
                email = email,
                body = email.cleanBody,
                traceMail = "test_mail",
                pdfDownloadStates = emptyMap(),
                onPdfAttachmentClick = {},
                onPdfSaveClick = {},
                savingStableIds = emptySet(),
                onImageLongPress = {}
            )
        }

        composeTestRule.waitForIdle()
    }
}
