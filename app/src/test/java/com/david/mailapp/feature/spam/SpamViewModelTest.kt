package com.david.mailapp.feature.spam

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.feature.inbox.ActionFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpamViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val email = Email("e1", "t1", "from", "F", "to", "S", "", 1000L,
        false, false, false, emptyList(), EmailFolder.Spam)

    // ── markNotSpam ─────────────────────────────────────────────

    @Test fun markNotSpam_success_enqueues_MarkedNotSpam() = runTest {
        val src = FakeSpamSource(notSpamResult = EmailActionResult.Success)
        src.room.value = listOf(email)
        val vm = SpamViewModel(src)
        advanceUntilIdle()

        vm.markNotSpam("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as SpamUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.MarkedNotSpam })
        assertFalse(state.activeActionEmailIds.contains("e1"))
        assertEquals(1, src.markNotSpamCalls)
    }

    @Test fun markNotSpam_failure_enqueues_Failure_not_success() = runTest {
        val src = FakeSpamSource(notSpamResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false))
        src.room.value = listOf(email)
        val vm = SpamViewModel(src)
        advanceUntilIdle()

        vm.markNotSpam("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as SpamUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.Failure })
        assertTrue(state.pendingFeedbackQueue.none { it is ActionFeedback.MarkedNotSpam })
    }

    // ── deleteToTrash ───────────────────────────────────────────

    @Test fun deleteToTrash_success_enqueues_MovedToTrashBatch() = runTest {
        val src = FakeSpamSource(deleteResult = EmailActionResult.Success)
        src.room.value = listOf(email)
        val vm = SpamViewModel(src)
        advanceUntilIdle()

        vm.deleteToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as SpamUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.MovedToTrashBatch })
        assertEquals(1, src.deleteCalls)
    }

    // ── Optimistic removal ──────────────────────────────────────

    @Test fun markNotSpam_hides_row_immediately_and_restores_it_on_failure() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeSpamSource(
            notSpamResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false),
            actionGate = gate
        )
        src.room.value = listOf(email)
        val vm = SpamViewModel(src)
        advanceUntilIdle()
        assertEquals(listOf("e1"), (vm.uiState.value as SpamUiState.Success).visibleEmails.map { it.id })

        vm.markNotSpam("e1")
        runCurrent()
        // Hidden instantly while the remote call is still in flight (gate open).
        assertTrue((vm.uiState.value as SpamUiState.Success).visibleEmails.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        // Remote failed → row comes back + error feedback.
        val state = vm.uiState.value as SpamUiState.Success
        assertEquals(listOf("e1"), state.visibleEmails.map { it.id })
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.Failure })
    }

    // ── Initial-load regression (mirrors Trash) ─────────────────

    @Test fun initial_load_empty_spam_stays_Loading_until_network_resolves() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeSpamSource(refreshGate = gate) // room starts empty
        val vm = SpamViewModel(src)
        runCurrent()
        src.refreshStarted.await()

        // Room emitted empty instantly, but the initial refresh is still in flight:
        // must NOT flip to the "no spam" state yet.
        assertTrue(
            "Must stay in Loading while first load is in flight",
            vm.uiState.value is SpamUiState.Loading
        )

        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Must resolve to Success after the network", state is SpamUiState.Success)
        assertTrue((state as SpamUiState.Success).emails.isEmpty())
        assertFalse(state.isRefreshing)
    }
}

private class FakeSpamSource(
    private val notSpamResult: EmailActionResult = EmailActionResult.Success,
    private val deleteResult: EmailActionResult = EmailActionResult.Success,
    private val refreshGate: CompletableDeferred<Unit>? = null,
    private val actionGate: CompletableDeferred<Unit>? = null
) : SpamEmailSource {
    val room = MutableStateFlow<List<Email>>(emptyList())
    var markNotSpamCalls = 0
    var deleteCalls = 0
    val refreshStarted = CompletableDeferred<Unit>()

    override fun observeSpam(): Flow<List<Email>> = room

    override suspend fun refreshSpam(pageToken: String?): PaginatedResult<Email> {
        refreshStarted.complete(Unit)
        refreshGate?.await()
        return PaginatedResult(emptyList(), null)
    }

    override suspend fun markNotSpam(emailId: String): EmailActionResult {
        markNotSpamCalls++
        actionGate?.await()
        return notSpamResult
    }

    override suspend fun deleteToTrash(emailId: String): EmailActionResult {
        deleteCalls++
        actionGate?.await()
        return deleteResult
    }
}
