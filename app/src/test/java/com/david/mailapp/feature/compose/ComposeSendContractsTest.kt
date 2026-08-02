package com.david.mailapp.feature.compose

import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
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
            override suspend fun getEmailById(emailId: String): Email? = null
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
            override suspend fun getEmailById(emailId: String): Email? = null
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
            override suspend fun getEmailById(emailId: String): Email? = null
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

    @Test
    fun `reply empieza bloqueado y no permite onSend durante la carga`() = runTest(mainDispatcher) {
        val originalEmail = Email(
            id = "1", threadId = "t1", from = "from@test.com", fromInitials = "F",
            to = "me@test.com", subject = "Subject", snippet = "", timestamp = 0L,
            isRead = false, isStarred = false, hasAttachments = false, labels = emptyList(),
            folder = com.david.mailapp.domain.model.EmailFolder.Inbox
        )
        val loadGate = CompletableDeferred<Email?>()
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = loadGate.await()
            override suspend fun sendEmail(to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?) {}
        }

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Reply("1"),
            emailSource = source,
            stringProvider = TestStringProvider()
        )

        // Verificamos que empieza bloqueado/cargando
        assertTrue("Debe empezar cargando", viewModel.uiState.value.isLoadingOriginalEmail)
        assertNull("No debe tener originalEmail cargado", viewModel.uiState.value.originalEmail)

        // Llamar a onSend() durante la carga no debe hacer nada
        viewModel.onToChanged("target@test.com")
        viewModel.onSend()
        testScheduler.runCurrent()
        assertFalse("No debe estar enviando", viewModel.uiState.value.isSending)

        // Completar la carga
        loadGate.complete(originalEmail)
        testScheduler.advanceUntilIdle()

        // Verificar inicialización correcta tras carga exitosa
        assertFalse("Debe terminar cargando", viewModel.uiState.value.isLoadingOriginalEmail)
        assertEquals(originalEmail, viewModel.uiState.value.originalEmail)
        assertEquals("from@test.com", viewModel.uiState.value.toField)
        assertEquals("test_string", viewModel.uiState.value.subject)
    }

    @Test
    fun `forward carga original conserva destinatario vacio y prepara asunto`() = runTest(mainDispatcher) {
        val originalEmail = Email(
            id = "forward-1", threadId = "thread-1", from = "from@test.com", fromInitials = "F",
            to = "me@test.com", subject = "Subject", snippet = "Original", timestamp = 0L,
            isRead = false, isStarred = false, hasAttachments = false, labels = emptyList(),
            folder = com.david.mailapp.domain.model.EmailFolder.Inbox
        )
        val loadGate = CompletableDeferred<Email?>()
        var sendCalls = 0
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = loadGate.await()
            override suspend fun sendEmail(
                to: String,
                cc: String?,
                bcc: String?,
                subject: String,
                body: String,
                replyContext: ReplyContext?
            ) {
                sendCalls++
            }
        }

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Forward(originalEmail.id),
            emailSource = source,
            stringProvider = TestStringProvider()
        )

        assertTrue("Forward debe empezar cargando", viewModel.uiState.value.isLoadingOriginalEmail)
        viewModel.onToChanged("target@test.com")
        viewModel.onSend()
        testScheduler.runCurrent()
        assertEquals("No debe enviar durante la carga", 0, sendCalls)

        loadGate.complete(originalEmail)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Forward debe terminar la carga", state.isLoadingOriginalEmail)
        assertEquals(ComposeMode.FORWARD, state.composeMode)
        assertEquals(originalEmail, state.originalEmail)
        assertEquals("", state.toField)
        assertEquals("test_string", state.subject)
        assertNull(state.originalEmailError)
    }

    @Test
    fun `original email nulo produce EMAIL_NOT_FOUND`() = runTest(mainDispatcher) {
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = null
            override suspend fun sendEmail(to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?) {}
        }
        val viewModel = ComposeViewModel(
            args = ComposeArgs.Reply("1"),
            emailSource = source,
            stringProvider = TestStringProvider()
        )
        testScheduler.advanceUntilIdle()
        assertEquals(UiErrorReason.EMAIL_NOT_FOUND, viewModel.uiState.value.originalEmailError)
    }

    @Test
    fun `original email excepcion produce UNKNOWN`() = runTest(mainDispatcher) {
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = throw RuntimeException("Room error")
            override suspend fun sendEmail(to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?) {}
        }
        val viewModel = ComposeViewModel(
            args = ComposeArgs.Reply("1"),
            emailSource = source,
            stringProvider = TestStringProvider()
        )
        testScheduler.advanceUntilIdle()
        assertEquals(UiErrorReason.UNKNOWN, viewModel.uiState.value.originalEmailError)
    }

    @Test
    fun `fuente de original que ignora cancelacion y responde tarde no actualiza estado`() = runTest(mainDispatcher) {
        val originalEmail = Email(
            id = "1", threadId = "t1", from = "from@test.com", fromInitials = "F",
            to = "me@test.com", subject = "Subject", snippet = "", timestamp = 0L,
            isRead = false, isStarred = false, hasAttachments = false, labels = emptyList(),
            folder = com.david.mailapp.domain.model.EmailFolder.Inbox
        )
        val loadGate = CompletableDeferred<Email?>()
        val loadStarted = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        var sourceCompleted = false
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? {
                loadStarted.complete(Unit)
                val result = try {
                    loadGate.await()
                } catch (e: CancellationException) {
                    cancellationObserved.complete(Unit)
                    withContext(NonCancellable) {
                        loadGate.await()
                    }
                }
                sourceCompleted = true
                return result
            }
            override suspend fun sendEmail(to: String, cc: String?, bcc: String?, subject: String, body: String, replyContext: ReplyContext?) {}
        }

        val viewModel = ComposeViewModel(
            args = ComposeArgs.Reply("1"),
            emailSource = source,
            stringProvider = TestStringProvider()
        )

        testScheduler.runCurrent()
        loadStarted.await()

        viewModel.viewModelScope.cancel()
        testScheduler.runCurrent()

        assertTrue("La fuente debe observar la cancelación", cancellationObserved.isCompleted)
        assertFalse("La fuente debe seguir bloqueada", sourceCompleted)

        loadGate.complete(originalEmail)
        testScheduler.advanceUntilIdle()

        assertTrue("La fuente no cooperativa debe haber retornado tarde", sourceCompleted)
        assertNull("No debe haber guardado el email original", viewModel.uiState.value.originalEmail)
        assertTrue("El estado de la instancia cerrada no debe publicarse como listo", viewModel.uiState.value.isLoadingOriginalEmail)
        assertNull("La respuesta tardía no debe publicar error", viewModel.uiState.value.originalEmailError)
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

    override suspend fun getEmailById(emailId: String): Email? = null

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
