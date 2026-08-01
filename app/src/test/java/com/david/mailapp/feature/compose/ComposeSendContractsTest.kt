package com.david.mailapp.feature.compose

import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val sentinel = CancellationException("sentinel-send")
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
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(
            "sendResult must be null after cancellation (no Error, no Success)",
            state.sendResult
        )
        assertFalse("isSending must be false after cancellation", state.isSending)
    }

    @Test
    fun `un error de envio libera el job y permite reintentar`() = runTest(mainDispatcher) {
        var shouldFail = true
        val fakeSource = object : ComposeEmailSource {
            var sendCallCount = 0
            override suspend fun getUserEmail(): String? = "test@example.com"
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?
            ) {
                sendCallCount++
                if (shouldFail) {
                    throw RuntimeException("Network Error")
                }
            }
        }

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Write,
            emailSource = fakeSource,
            stringProvider = TestStringProvider()
        )

        viewModel.onToChanged("to@test.com")
        testScheduler.advanceUntilIdle()

        // First attempt (fails)
        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        val state1 = viewModel.uiState.value
        assertTrue("Expected Error", state1.sendResult is SendResult.Error)
        assertFalse(state1.isSending)
        assertEquals(1, fakeSource.sendCallCount)

        // Second attempt (succeeds)
        shouldFail = false
        viewModel.onSend()
        testScheduler.advanceUntilIdle()

        val state2 = viewModel.uiState.value
        assertTrue("Expected Success", state2.sendResult is SendResult.Success)
        assertFalse(state2.isSending)
        assertEquals(2, fakeSource.sendCallCount)
    }

    @Test
    fun `cancelar el scope durante el envio deja sendResult null e isSending false y registra la cancelacion en fake`() = runTest(mainDispatcher) {
        val sendGate = CompletableDeferred<Unit>()
        val fakeSource = FakeComposeEmailSource(sendGate = sendGate)

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Write,
            emailSource = fakeSource,
            stringProvider = TestStringProvider()
        )

        viewModel.onToChanged("to@test.com")
        testScheduler.advanceUntilIdle()

        viewModel.onSend()
        testScheduler.advanceUntilLowerThan(100) // Advance a bit but not complete

        // Cancel the viewModelScope
        viewModel.viewModelScope.cancel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("sendResult must be null", state.sendResult)
        assertFalse("isSending must be false", state.isSending)
        assertTrue("Fake must register cancellation", fakeSource.wasCancelled)
    }

    @Test
    fun `fuente que ignora cancelacion y responde tarde no publica resultado`() = runTest(mainDispatcher) {
        val sendGate = CompletableDeferred<Unit>()
        val sendStarted = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        var completedNormally = false
        val fakeSource = object : ComposeEmailSource {
            var sendCallCount = 0
            override suspend fun getUserEmail(): String? = "test@example.com"
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?
            ) {
                sendCallCount++
                sendStarted.complete(Unit)
                try {
                    sendGate.await()
                } catch (e: CancellationException) {
                    cancellationObserved.complete(Unit)
                    withContext(NonCancellable) {
                        sendGate.await()
                    }
                }
                completedNormally = true
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
        testScheduler.runCurrent()
        sendStarted.await()

        // Cancel scope to simulate screen dismissal
        viewModel.viewModelScope.cancel()
        testScheduler.runCurrent()
        assertTrue("Fake should observe cancellation", cancellationObserved.isCompleted)
        assertFalse("Fake must still be blocked after cancellation", completedNormally)

        // Release the uncooperative source only after cancellation was observed.
        sendGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertTrue("Fake should have completed after cancellation", completedNormally)
        val state = viewModel.uiState.value
        assertNull("Late response must not publish success/error", state.sendResult)
        assertFalse("isSending must be false", state.isSending)
    }

    private fun kotlinx.coroutines.test.TestCoroutineScheduler.advanceUntilLowerThan(limit: Long) {
        advanceTimeBy(limit)
        runCurrent()
    }
}

class FakeComposeEmailSource(
    private val emailResult: String? = "test@example.com",
    private val sendGate: CompletableDeferred<Unit>? = null
) : ComposeEmailSource {
    var sendCallCount = 0
        private set
    var wasCancelled = false
        private set

    override suspend fun getUserEmail(): String? = emailResult

    override suspend fun sendEmail(
        to: String, cc: String?, bcc: String?,
        subject: String, body: String,
        replyContext: ReplyContext?
    ) {
        sendCallCount++
        try {
            sendGate?.await()
        } catch (e: CancellationException) {
            wasCancelled = true
            throw e
        }
    }
}

class TestStringProvider : StringProvider {
    override fun getString(resId: Int, vararg formatArgs: Any): String = "test_string"
}
