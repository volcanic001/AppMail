package com.david.mailapp.feature.emaildetail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.R
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.EmailDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EmailDetailStateContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryableBodyError_showsMessageAndInvokesRetryOnce() {
        var retryCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                EmailDetailBodyError(
                    state = EmailDetailUiState.BodyError(
                        email = null,
                        reason = UiErrorReason.EMAIL_BODY_LOAD_FAILED,
                        retryable = true
                    ),
                    pdfDownloadStates = emptyMap(),
                    onPdfAttachmentClick = {},
                    onPdfSaveClick = {},
                    savingStableIds = emptySet(),
                    onRetry = { retryCount++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.detail_body_load_error))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun nonRetryableBodyError_hidesRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                EmailDetailBodyError(
                    state = EmailDetailUiState.BodyError(
                        email = null,
                        reason = UiErrorReason.EMAIL_NOT_FOUND,
                        retryable = false
                    ),
                    pdfDownloadStates = emptyMap(),
                    onPdfAttachmentClick = {},
                    onPdfSaveClick = {},
                    savingStableIds = emptySet(),
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.detail_email_not_found))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry))
            .assertDoesNotExist()
    }

    @Test
    fun bodyErrorWithPdf_forwardsOpenAndSaveCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = PdfAttachmentMetadata(
            fileName = PDF_NAME,
            mimeType = "application/pdf",
            attachmentId = "attachment-1",
            sizeBytes = 4_096L,
            partId = "part-1"
        )
        val email = Email(
            id = "body-error-email",
            threadId = "thread-1",
            from = "fixture@example.com",
            fromInitials = "F",
            to = "receiver@example.com",
            subject = "BodyError fixture",
            snippet = "fixture",
            timestamp = 1L,
            isRead = true,
            isStarred = false,
            hasAttachments = true,
            labels = emptyList(),
            folder = EmailFolder.Inbox,
            pdfAttachments = listOf(attachment),
            pdfMetadataScanned = true
        )
        var opened: PdfAttachmentMetadata? = null
        var saved: PdfAttachmentMetadata? = null

        composeRule.setContent {
            MaterialTheme {
                EmailDetailBodyError(
                    state = EmailDetailUiState.BodyError(
                        email = email,
                        reason = UiErrorReason.EMAIL_BODY_PDFS_ONLY,
                        retryable = false
                    ),
                    pdfDownloadStates = mapOf(
                        attachment.stableId to PdfDownloadState.Ready(attachment.sizeBytes!!)
                    ),
                    onPdfAttachmentClick = { opened = it },
                    onPdfSaveClick = { saved = it },
                    savingStableIds = emptySet(),
                    onRetry = {}
                )
            }
        }

        composeRule.onNode(hasText(PDF_NAME) and hasClickAction())
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(attachment, opened) }

        composeRule.onNodeWithContentDescription(context.getString(R.string.pdf_save_as))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(attachment, saved) }
    }

    @Test
    fun emptyWithPdf_isNeutralWithoutRetry_andForwardsPdfActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = PdfAttachmentMetadata(
            fileName = EMPTY_PDF_NAME,
            mimeType = "application/pdf",
            attachmentId = "empty-attachment",
            sizeBytes = 2_048L,
            partId = "empty-part"
        )
        val email = Email(
            id = "empty-email",
            threadId = "thread-empty",
            from = "fixture@example.com",
            fromInitials = "F",
            to = "receiver@example.com",
            subject = "Empty fixture",
            snippet = "fixture",
            timestamp = 1L,
            isRead = true,
            isStarred = false,
            hasAttachments = true,
            labels = emptyList(),
            folder = EmailFolder.Inbox,
            pdfAttachments = listOf(attachment),
            pdfMetadataScanned = true,
            contentState = com.david.mailapp.domain.model.EmailContentState.EMPTY
        )
        var opened: PdfAttachmentMetadata? = null
        var saved: PdfAttachmentMetadata? = null

        composeRule.setContent {
            MaterialTheme {
                EmailDetailEmpty(
                    state = EmailDetailUiState.Empty(email),
                    pdfDownloadStates = mapOf(
                        attachment.stableId to PdfDownloadState.Ready(attachment.sizeBytes!!)
                    ),
                    onPdfAttachmentClick = { opened = it },
                    onPdfSaveClick = { saved = it },
                    savingStableIds = emptySet()
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.detail_body_empty_with_pdfs))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry))
            .assertDoesNotExist()
        composeRule.onNode(hasText(EMPTY_PDF_NAME) and hasClickAction())
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.pdf_save_as))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(attachment, opened)
            assertEquals(attachment, saved)
        }
    }

    private companion object {
        const val PDF_NAME = "body-error-fixture.pdf"
        const val EMPTY_PDF_NAME = "empty-fixture.pdf"
    }
}
