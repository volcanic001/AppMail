package com.david.mailapp.feature.emaildetail

import android.view.View
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.R
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.testhelpers.testEmail
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EmailDetailPresentationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ═══════════════════════════════════════════════════════════════
    // 1. Loading
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun loading_showsProgressAndDisablesReplyForward() {
        var backCount = 0

        composeRule.setPresentation(
            uiState = EmailDetailUiState.Loading,
            snackbarHostState = SnackbarHostState(),
            onBack = { backCount++ }
        )

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_reply))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_forward))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Retryable resolution error
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun retryableResolutionError_showsMessageAndForwardsRetry() {
        var retryCount = 0

        composeRule.setPresentation(
            uiState = EmailDetailUiState.ResolutionError(
                reason = UiErrorReason.NO_CONNECTION,
                retryable = true
            ),
            snackbarHostState = SnackbarHostState(),
            onRetry = { retryCount++ }
        )

        composeRule.onNodeWithText(context.getString(R.string.error_no_connection))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Non-retryable resolution error
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun nonRetryableResolutionError_hidesRetry() {
        composeRule.setPresentation(
            uiState = EmailDetailUiState.ResolutionError(
                reason = UiErrorReason.EMAIL_NOT_FOUND,
                retryable = false
            ),
            snackbarHostState = SnackbarHostState()
        )

        composeRule.onNodeWithText(context.getString(R.string.detail_email_not_found))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry))
            .assertDoesNotExist()
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Body error without attachments
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun bodyErrorWithoutAttachments_showsErrorWithoutPdfActions() {
        val email = testEmail(id = "be-no-pdf", subject = "Sin adjuntos")

        composeRule.setPresentation(
            uiState = EmailDetailUiState.BodyError(
                email = email,
                reason = UiErrorReason.EMAIL_BODY_LOAD_FAILED,
                retryable = false
            ),
            snackbarHostState = SnackbarHostState()
        )

        composeRule.onNodeWithText(context.getString(R.string.detail_body_load_error))
            .assertIsDisplayed()
        composeRule.onNodeWithText(PDF_NAME)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.pdf_save_as))
            .assertDoesNotExist()
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Body error with attachment — callbacks and saving state
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun bodyErrorWithAttachment_forwardsPdfCallbacksAndSavingState() {
        val attachment = PdfAttachmentMetadata(
            fileName = PDF_NAME,
            mimeType = "application/pdf",
            attachmentId = "attachment-1",
            sizeBytes = 4_096L,
            partId = "part-1"
        )
        val email = testEmail(id = "be-with-pdf", subject = "Con PDF").copy(
            hasAttachments = true,
            pdfAttachments = listOf(attachment),
            pdfMetadataScanned = true
        )
        var opened: PdfAttachmentMetadata? = null
        var saved: PdfAttachmentMetadata? = null
        val savingIds = mutableStateOf(emptySet<String>())

        composeRule.setContent {
            MaterialTheme {
                EmailDetailPresentation(
                    uiState = EmailDetailUiState.BodyError(
                        email = email,
                        reason = UiErrorReason.EMAIL_BODY_PDFS_ONLY,
                        retryable = false
                    ),
                    pdfDownloadStates = mapOf(
                        attachment.stableId to PdfDownloadState.Ready(attachment.sizeBytes!!)
                    ),
                    savingStableIds = savingIds.value,
                    traceMail = TRACE_KEY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onReply = {},
                    onForward = {},
                    onRetry = {},
                    onRetryBody = {},
                    onPdfAttachmentClick = { opened = it },
                    onPdfSaveClick = { saved = it },
                    modifier = Modifier
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

        // Recompute with the stableId in savingStableIds: the Save action
        // disappears and an indeterminate progress indicator takes its place.
        composeRule.runOnIdle { savingIds.value = setOf(attachment.stableId) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.pdf_save_as))
            .assertDoesNotExist()
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Reply/Forward/Back only in Ready
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun replyForwardAndBack_areForwardedOnlyInReady() {
        val email = testEmail(id = "rfb", subject = "RFB")
        val currentState = mutableStateOf<EmailDetailUiState>(EmailDetailUiState.Loading)
        var replyId: String? = null
        var forwardId: String? = null
        var backCount = 0

        composeRule.setContent {
            MaterialTheme {
                EmailDetailPresentation(
                    uiState = currentState.value,
                    pdfDownloadStates = emptyMap(),
                    savingStableIds = emptySet(),
                    traceMail = TRACE_KEY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = { backCount++ },
                    onReply = { replyId = it },
                    onForward = { forwardId = it },
                    onRetry = {},
                    onRetryBody = {},
                    onPdfAttachmentClick = {},
                    onPdfSaveClick = {},
                    modifier = Modifier
                )
            }
        }

        val nonReadyStates = listOf<EmailDetailUiState>(
            EmailDetailUiState.Loading,
            EmailDetailUiState.ResolutionError(
                reason = UiErrorReason.NO_CONNECTION,
                retryable = true
            ),
            EmailDetailUiState.PreparingBody(email),
            EmailDetailUiState.BodyError(
                email = email,
                reason = UiErrorReason.EMAIL_BODY_LOAD_FAILED,
                retryable = false
            )
        )

        for (state in nonReadyStates) {
            composeRule.runOnIdle { currentState.value = state }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(context.getString(R.string.detail_reply))
                .assertIsDisplayed()
                .assertIsNotEnabled()
            composeRule.onNodeWithContentDescription(context.getString(R.string.detail_forward))
                .assertIsDisplayed()
                .assertIsNotEnabled()
        }

        // Ready: actions enabled and forward the exact email id.
        composeRule.runOnIdle { currentState.value = EmailDetailUiState.Ready(email) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_reply))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_forward))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals("rfb", replyId)
            assertEquals("rfb", forwardId)
        }

        // Back: a single invocation of onBack.
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Expanded header consumes back before the outer handler
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun expandedHeader_consumesBackBeforeOuterHandler() {
        val email = testEmail(
            id = "hdr",
            subject = "Header Subject",
            from = "sender@test.com"
        )
        var outerBackCount = 0

        composeRule.setContent {
            // Outer handler registered first: Presentation's BackHandler
            // stays on top of the dispatcher stack while enabled.
            BackHandler { outerBackCount++ }
            MaterialTheme {
                EmailDetailPresentation(
                    uiState = EmailDetailUiState.Ready(email),
                    pdfDownloadStates = emptyMap(),
                    savingStableIds = emptySet(),
                    traceMail = TRACE_KEY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onReply = {},
                    onForward = {},
                    onRetry = {},
                    onRetryBody = {},
                    onPdfAttachmentClick = {},
                    onPdfSaveClick = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_expand_header))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("Header Subject")
            .assertIsDisplayed()
        composeRule.onNodeWithText("sender@test.com")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_collapse_header))
            .assertIsDisplayed()

        // First back: collapses the header, outer handler untouched.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(0, outerBackCount) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_expand_header))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_collapse_header))
            .assertDoesNotExist()

        // Second back: reaches the outer handler exactly once.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, outerBackCount) }
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Image long-press → action menu → fullscreen dialog
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun imageLongPress_opensActionMenuAndFullscreen() {
        val email = testEmail(id = "img", subject = "Image fixture").copy(
            body = FULL_VIEWPORT_IMAGE_HTML,
            cleanBody = FULL_VIEWPORT_IMAGE_HTML,
            pdfMetadataScanned = true
        )

        composeRule.setPresentation(
            uiState = EmailDetailUiState.Ready(email),
            snackbarHostState = SnackbarHostState()
        )

        // The Compose loader hides only after the WebView rendered the page.
        composeRule.waitUntilDoesNotExist(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
            timeoutMillis = 15_000
        )

        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForWebViewProgress(100))
            .perform(ViewActions.longClick())

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.image_open))
                    .fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.image_save))
            .assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.image_fullscreen))
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════════
    // Fixtures & helpers
    // ═══════════════════════════════════════════════════════════════

    private fun ComposeContentTestRule.setPresentation(
        uiState: EmailDetailUiState,
        snackbarHostState: SnackbarHostState,
        pdfDownloadStates: Map<String, PdfDownloadState> = emptyMap(),
        savingStableIds: Set<String> = emptySet(),
        onBack: () -> Unit = {},
        onReply: (String) -> Unit = {},
        onForward: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onRetryBody: () -> Unit = {},
        onPdfAttachmentClick: (PdfAttachmentMetadata) -> Unit = {},
        onPdfSaveClick: (PdfAttachmentMetadata) -> Unit = {}
    ) {
        setContent {
            MaterialTheme {
                EmailDetailPresentation(
                    uiState = uiState,
                    pdfDownloadStates = pdfDownloadStates,
                    savingStableIds = savingStableIds,
                    traceMail = TRACE_KEY,
                    snackbarHostState = snackbarHostState,
                    onBack = onBack,
                    onReply = onReply,
                    onForward = onForward,
                    onRetry = onRetry,
                    onRetryBody = onRetryBody,
                    onPdfAttachmentClick = onPdfAttachmentClick,
                    onPdfSaveClick = onPdfSaveClick,
                    modifier = Modifier
                )
            }
        }
    }

    private class WaitForWebViewProgress(
        private val target: Int,
        private val timeoutMillis: Long = 15_000
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

        override fun getDescription(): String = "wait for WebView progress == $target"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = System.currentTimeMillis()
            // A fresh WebView may report progress 100 before any document is
            // loaded; require a loaded document (non-null url) as well.
            while (webView.progress < target || webView.url == null) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    throw RuntimeException(
                        "WebView did not reach progress $target " +
                            "(progress=${webView.progress} url=${webView.url})"
                    )
                }
                uiController.loopMainThreadForAtLeast(50)
            }
            uiController.loopMainThreadForAtLeast(500)
        }
    }

    private companion object {
        const val TRACE_KEY = "4_3_presentation_test"
        const val PDF_NAME = "presentation-fixture.pdf"
        const val VALID_IMAGE_DATA_URI =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        const val FULL_VIEWPORT_IMAGE_HTML =
            """<img src="$VALID_IMAGE_DATA_URI" style="width:100%;height:100vh;">"""
    }
}
