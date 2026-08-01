package com.david.mailapp.feature.compose

import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Before
import org.junit.Test

/**
 * Contract test C8 — Double send prevention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeSendContractsTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Ignore("Contrato pendiente: Fase 3.4")
    @Test
    fun `C8 doble onSend produce una sola peticion remota`() = runTest(mainDispatcher) {
        val sendGate = CompletableDeferred<Unit>()
        val fakeSource = FakeComposeEmailSource(sendGate = sendGate)

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Write,
            emailSource = fakeSource,
            stringProvider = TestStringProvider()
        )

        viewModel.onToChanged("to@test.com")
        viewModel.onSubjectChanged("Test")
        viewModel.onBodyChanged("Body")

        testScheduler.advanceUntilIdle()

        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        sendGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("Should have exactly 1 send call", 1, fakeSource.sendCallCount)
        assertFalse("Should not be sending", viewModel.uiState.value.isSending)
        assertTrue("Should have success result", viewModel.uiState.value.sendResult is SendResult.Success)
    }

    @Ignore("Contrato pendiente: Fase 3.4")
    @Test
    fun `C8 doble onSend conserva un solo resultado final`() = runTest(mainDispatcher) {
        val sendGate = CompletableDeferred<Unit>()
        val fakeSource = FakeComposeEmailSource(sendGate = sendGate)

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Write,
            emailSource = fakeSource,
            stringProvider = TestStringProvider()
        )

        viewModel.onToChanged("to@test.com")
        viewModel.onSubjectChanged("Test")

        testScheduler.advanceUntilIdle()

        viewModel.onSend()
        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        sendGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.sendResult
        assertTrue("Expected Success", result is SendResult.Success)
        assertEquals("Should have exactly 1 send call", 1, fakeSource.sendCallCount)
    }
    @Test
    fun `cancelacion de sendEmail no produce SendResult Error ni Success`() = runTest(mainDispatcher) {
        val sentinel = kotlinx.coroutines.CancellationException("sentinel-send")
        val fakeSource = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String? = "test@example.com"
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?
            ) {
                throw sentinel
            }
        }

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Write,
            emailSource = fakeSource,
            stringProvider = TestStringProvider()
        )

        viewModel.onToChanged("to@test.com")
        testScheduler.advanceUntilIdle()

        viewModel.onSend()
        // CancellationException inside viewModelScope.launch cancels the child coroutine;
        // it does NOT propagate to the test scope. Verify observable contract only.
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        org.junit.Assert.assertNull(
            "sendResult must be null after cancellation (no Error, no Success)",
            state.sendResult
        )
        assertFalse("isSending must be false after cancellation", state.isSending)
    }
}


class FakeComposeEmailSource(
    private val emailResult: String? = "test@example.com",
    private val sendGate: CompletableDeferred<Unit>? = null
) : ComposeEmailSource {
    var sendCallCount = 0
        private set

    override suspend fun getUserEmail(): String? = emailResult

    override suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext?
    ) {
        sendCallCount++
        sendGate?.await()
    }
}

class TestStringProvider : StringProvider {
    override fun getString(resId: Int, vararg formatArgs: Any): String = "test_string"
}
