package com.david.mailapp.feature.emaildetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.repository.EmailResolutionFailureReason
import com.david.mailapp.data.repository.EmailResolutionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailDetailViewModelResolutionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() {
        viewModelStores.forEach(ViewModelStore::clear)
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    // ── helpers ────────────────────────────────────────────────

    private fun createViewModel(source: FakeEmailDetailSource): EmailDetailViewModel {
        val vm = newViewModel(source)
        advanceUntilIdle()
        return vm
    }

    private fun newViewModel(source: FakeEmailDetailSource): EmailDetailViewModel {
        val store = ViewModelStore().also(viewModelStores::add)
        return ViewModelProvider(
            store,
            EmailDetailViewModel.Factory("e1", source, testDispatcher)
        )[EmailDetailViewModel::class.java]
    }

    private fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun runCurrent() {
        testDispatcher.scheduler.runCurrent()
    }

    // ═══════════════════════════════════════════════════════════
    // Resolution
    // ═══════════════════════════════════════════════════════════

    @Test
    fun staysInLoading_whileResolution_isSuspended() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveGate = CompletableDeferred()
        val vm = newViewModel(source)
        runCurrent()

        assertEquals(EmailDetailUiState.Loading, vm.uiState.value)
        source.resolveGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun nullRoomEmission_neverProduces_notFound() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(body = "<html>ok</html>", bodyBlank = false, pdfScanned = true)
        )
        source.injectInlineImagesResult = "<html>ok</html>"
        val vm = createViewModel(source)

        assertTrue(vm.uiState.value is EmailDetailUiState.Ready)

        // Emit null from Room — must not change state
        source.emitRoomEmail(null)
        advanceUntilIdle()
        assertTrue("null Room after Found is ignored", vm.uiState.value is EmailDetailUiState.Ready)
    }

    @Test
    fun notFound_producesResolutionError_notRetryable() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.NotFound
        val vm = createViewModel(source)

        val state = vm.uiState.value as EmailDetailUiState.ResolutionError
        assertEquals(UiErrorReason.EMAIL_NOT_FOUND, state.reason)
        assertEquals(false, state.retryable)
    }

    @Test
    fun eachResolutionFailure_mapsReasonAndRetryableCorrectly() = runTest {
        val cases = mapOf(
            EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT to Pair(UiErrorReason.NO_ACTIVE_ACCOUNT, false),
            EmailResolutionFailureReason.SESSION_CHANGED to Pair(UiErrorReason.ACCOUNT_CHANGED, false),
            EmailResolutionFailureReason.SESSION_EXPIRED to Pair(UiErrorReason.SESSION_EXPIRED, false),
            EmailResolutionFailureReason.NO_CONNECTION to Pair(UiErrorReason.NO_CONNECTION, true),
            EmailResolutionFailureReason.TEMPORARY_REMOTE to Pair(UiErrorReason.EMAIL_TEMPORARILY_UNAVAILABLE, true),
            EmailResolutionFailureReason.INVALID_RESPONSE to Pair(UiErrorReason.EMAIL_RESOLUTION_FAILED, true),
            EmailResolutionFailureReason.REMOTE_REJECTED to Pair(UiErrorReason.EMAIL_ACCESS_DENIED, false),
            EmailResolutionFailureReason.INVALID_ID to Pair(UiErrorReason.EMAIL_INVALID_REFERENCE, false),
            EmailResolutionFailureReason.LOCAL_READ_FAILED to Pair(UiErrorReason.EMAIL_LOCAL_CACHE_FAILED, true),
            EmailResolutionFailureReason.LOCAL_WRITE_FAILED to Pair(UiErrorReason.EMAIL_LOCAL_CACHE_FAILED, true),
        )
        for ((failureReason, expected) in cases) {
            val source = FakeEmailDetailSource("e1")
            source.resolveResult = EmailResolutionResult.Failure(failureReason)
            val vm = createViewModel(source)

            val state = vm.uiState.value as EmailDetailUiState.ResolutionError
            assertEquals("$failureReason reason", expected.first, state.reason)
            assertEquals("$failureReason retryable", expected.second, state.retryable)
        }
    }

    @Test
    fun unexpectedResolutionException_producesRetryableResolutionFailure() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveError = IllegalStateException("unexpected")

        val vm = createViewModel(source)

        val state = vm.uiState.value as EmailDetailUiState.ResolutionError
        assertEquals(UiErrorReason.EMAIL_RESOLUTION_FAILED, state.reason)
        assertTrue(state.retryable)
    }

    // ═══════════════════════════════════════════════════════════
    // Body preparation
    // ═══════════════════════════════════════════════════════════

    @Test
    fun foundWithBody_proceedsToReady() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(body = "<html>done</html>", bodyBlank = false, pdfScanned = true)
        )
        source.injectInlineImagesResult = "<html>done</html>"
        val vm = createViewModel(source)

        assertTrue(vm.uiState.value is EmailDetailUiState.Ready)
    }

    @Test
    fun foundWithoutBody_emitsPreparingBody() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchGate = CompletableDeferred()
        val vm = newViewModel(source)
        runCurrent()

        assertTrue("email without body → PreparingBody",
            vm.uiState.value is EmailDetailUiState.PreparingBody)
        source.bodyFetchGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun repeatedRoomEmissions_doNotDuplicateResolution_body_or_read() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(body = "<html>r</html>", bodyBlank = false)
        )
        source.injectInlineImagesResult = "<html>r</html>"
        val vm = createViewModel(source)

        assertEquals("one resolution call", 1, source.resolveCallCount)
        assertEquals("one read call", 1, source.markAsReadCallCount)
        assertEquals("one body fetch", 1, source.bodyFetchCallCount)

        // Re-emit the same email from Room
        source.emitRoomEmail(
            FakeEmailDetailSource.sampleEmail(body = "<html>r</html>", bodyBlank = false)
        )
        advanceUntilIdle()

        assertEquals("still one resolution call", 1, source.resolveCallCount)
        assertEquals("still one read call", 1, source.markAsReadCallCount)
        assertEquals("still one body fetch", 1, source.bodyFetchCallCount)
    }

    // ═══════════════════════════════════════════════════════════
    // Retry
    // ═══════════════════════════════════════════════════════════

    @Test
    fun retryResolution_executesSingleCall() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Failure(EmailResolutionFailureReason.NO_CONNECTION)
        val vm = createViewModel(source)

        assertTrue(vm.uiState.value is EmailDetailUiState.ResolutionError)
        assertEquals(1, source.resolveCallCount)

        // Change result for retry
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(body = "<html>ok</html>", bodyBlank = false)
        )
        source.injectInlineImagesResult = "<html>ok</html>"

        // Tap retry multiple times — only one resolution
        vm.onRetry()
        vm.onRetry()
        vm.onRetry()
        advanceUntilIdle()

        assertEquals(2, source.resolveCallCount)
    }

    @Test
    fun nonRetryable_error_ignores_onRetry() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Failure(EmailResolutionFailureReason.SESSION_EXPIRED)
        val vm = createViewModel(source)

        assertTrue(vm.uiState.value is EmailDetailUiState.ResolutionError)
        val error = vm.uiState.value as EmailDetailUiState.ResolutionError
        assertEquals(false, error.retryable)

        vm.onRetry()
        assertEquals("retry ignored", 1, source.resolveCallCount)
    }

    // ═══════════════════════════════════════════════════════════
    // Retry body
    // ═══════════════════════════════════════════════════════════

    @Test
    fun retryBody_recoverableFailure_thenRoomEmission_finishesReady() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchResult = null
        val vm = createViewModel(source)

        val firstFailure = vm.uiState.value as EmailDetailUiState.BodyError
        assertTrue(firstFailure.retryable)
        assertEquals(1, source.bodyFetchCallCount)

        val recoveredEmail = FakeEmailDetailSource.sampleEmail(
            body = "<html>recovered</html>",
            bodyBlank = false,
            pdfScanned = true
        )
        source.bodyFetchResult = BodyFetchResult(
            rawBody = recoveredEmail.body,
            inlineRefs = emptyList()
        )
        source.onBodyFetch = { callCount ->
            if (callCount == 2) source.emitRoomEmail(recoveredEmail)
        }

        vm.onRetryBody()
        advanceUntilIdle()

        val ready = vm.uiState.value as EmailDetailUiState.Ready
        assertEquals("<html>recovered</html>", ready.email.body)
        assertEquals(2, source.bodyFetchCallCount)
    }

    @Test
    fun retryBody_recoverableFailure_thenSecondFailure_returnsBodyError() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchResult = null
        val vm = createViewModel(source)

        assertTrue(vm.uiState.value is EmailDetailUiState.BodyError)
        assertEquals(1, source.bodyFetchCallCount)

        vm.onRetryBody()
        advanceUntilIdle()

        val secondFailure = vm.uiState.value as EmailDetailUiState.BodyError
        assertTrue(secondFailure.retryable)
        assertEquals(2, source.bodyFetchCallCount)
    }

    @Test
    fun retryBody_multipleTaps_produceExactlyOneAdditionalCall() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchResult = null
        val vm = createViewModel(source)
        assertEquals(1, source.bodyFetchCallCount)

        val retryGate = CompletableDeferred<Unit>()
        source.bodyFetchGate = retryGate

        vm.onRetryBody()
        vm.onRetryBody()
        vm.onRetryBody()
        runCurrent()

        assertEquals("only one retry fetch", 2, source.bodyFetchCallCount)
        assertTrue(vm.uiState.value is EmailDetailUiState.PreparingBody)

        retryGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is EmailDetailUiState.BodyError)
    }

    @Test
    fun retryBody_nonRetryableBodyError_isIgnored() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchResult = BodyFetchResult(
            rawBody = "",
            inlineRefs = emptyList()
        )
        val vm = createViewModel(source)

        val error = vm.uiState.value as EmailDetailUiState.BodyError
        assertEquals(false, error.retryable)
        assertEquals(1, source.bodyFetchCallCount)

        vm.onRetryBody()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is EmailDetailUiState.BodyError)
        assertEquals("non-retryable error must not fetch again", 1, source.bodyFetchCallCount)
    }

    @Test
    fun pdfsOnly_isNotRetryable() = runTest {
        val source = FakeEmailDetailSource("e1")
        source.resolveResult = EmailResolutionResult.Found(
            FakeEmailDetailSource.sampleEmail(bodyBlank = true)
        )
        source.bodyFetchResult = BodyFetchResult(
            rawBody = null,
            inlineRefs = emptyList(),
            pdfAttachments = listOf(
                com.david.mailapp.domain.model.PdfAttachmentMetadata(
                    "doc.pdf", "application/pdf", "att1", 1024L
                )
            )
        )
        val vm = createViewModel(source)

        val error = vm.uiState.value as EmailDetailUiState.BodyError
        assertEquals(UiErrorReason.EMAIL_BODY_PDFS_ONLY, error.reason)
        assertEquals("PDFs only → no body retry", false, error.retryable)
        assertTrue("PDF metadata preserved", error.email?.pdfAttachments?.isNotEmpty() == true)
    }
}
