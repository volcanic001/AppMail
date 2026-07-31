package com.david.mailapp.feature.inbox

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelActionTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val email = Email("e1", "t1", "from", "F", "to", "S", "", 1000L,
        false, false, false, emptyList(), EmailFolder.Inbox)

    // ── Move to Trash: block + enqueue success ──────────────────

    @Test fun moveToTrash_success_enqueues_MovedToTrash_and_allows_undo() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.MovedToTrash })
        assertFalse(state.activeActionEmailIds.contains("e1"))
        assertEquals(1, src.moveToTrashCalls)
    }

    // ── Move to Trash: block + enqueue failure ──────────────────

    @Test fun moveToTrash_failure_enqueues_Failure_no_undo() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.Failure })
        assertTrue(state.pendingFeedbackQueue.none { it is ActionFeedback.MovedToTrash })
    }

    // ── Duplicate block ─────────────────────────────────────────

    @Test fun moveToTrash_duplicate_call_is_blocked() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success, moveToTrashGate = gate)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        // Second call while first is suspended → blocked
        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.activeActionEmailIds.contains("e1"))

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, src.moveToTrashCalls)
    }

    // ── Undo as new operation ────────────────────────────────────

    @Test fun undo_as_new_remote_operation_enqueues_RestoredToInbox() = runTest {
        val src = FakeInboxSource(restoreFromTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.undoMoveToTrash("e2")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.RestoredToInbox })
        assertEquals(1, src.restoreFromTrashCalls)
    }

    // ── markAsRead silent success, visible failure ──────────────

    @Test fun markAsRead_success_is_silent() = runTest {
        val src = FakeInboxSource(markAsReadResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.markAsRead("e3")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.isEmpty())
    }

    @Test fun markAsRead_failure_is_visible() = runTest {
        val src = FakeInboxSource(markAsReadResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.markAsRead("e3")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.Failure })
    }

    // ── Concurrent different IDs ────────────────────────────────

    @Test fun concurrent_different_ids_both_enqueue_feedback() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(
            moveToTrashResult = EmailActionResult.Success,
            moveToTrashGate = gate
        )
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("a")
        vm.moveToTrash("b")
        runCurrent()

        val active = vm.uiState.value as InboxUiState.Success
        assertEquals(setOf("a", "b"), active.activeActionEmailIds)
        assertEquals(2, src.moveToTrashCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertEquals(2, src.moveToTrashCalls)
        assertEquals(2, state.pendingFeedbackQueue.size)
    }

    @Test fun rapid_offline_failures_for_different_ids_are_all_enqueued() = runTest {
        val src = FakeInboxSource(
            moveToTrashResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false)
        )
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        repeat(5) { index -> vm.moveToTrash("e$index") }
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertEquals(5, src.moveToTrashCalls)
        assertEquals(5, state.pendingFeedbackQueue.size)
        assertEquals(5, state.pendingFeedbackQueue.map { it.id }.toSet().size)
        assertTrue(state.pendingFeedbackQueue.all { it is ActionFeedback.Failure })
        assertTrue(state.activeActionEmailIds.isEmpty())
    }

    // ── Consume feedback ────────────────────────────────────────

    @Test fun consume_feedback_removes_it_from_queue() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()
        vm.moveToTrash("e1")
        advanceUntilIdle()

        val f = (vm.uiState.value as InboxUiState.Success).pendingFeedbackQueue.first()
        vm.consumeFeedback(f.id)
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.isEmpty())
    }

    @Test fun unexpected_exception_enqueues_failure_and_releases_action() = runTest {
        val src = FakeInboxSource(moveToTrashError = IOException("offline"))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.single() is ActionFeedback.Failure)
        assertFalse("e1" in state.activeActionEmailIds)
    }

    @Test fun cancellation_releases_action_without_feedback() = runTest {
        val src = FakeInboxSource(moveToTrashError = CancellationException("cancelled"))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.isEmpty())
        assertFalse("e1" in state.activeActionEmailIds)
    }

    @Test fun room_refresh_and_pagination_preserve_action_state() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(
            moveToTrashGate = gate,
            markAsReadResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false),
            refreshResult = PaginatedResult(emptyList(), "page-2")
        )
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.markAsRead("feedback")
        advanceUntilIdle()
        vm.moveToTrash("active")
        runCurrent()

        src.room.value = listOf(email)
        runCurrent()
        vm.refresh()
        runCurrent()

        var state = vm.uiState.value as InboxUiState.Success
        assertTrue("active" in state.activeActionEmailIds)
        assertEquals(1, state.pendingFeedbackQueue.size)
        assertEquals(listOf(email), state.emails)
        assertTrue(state.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        vm.loadNextPage()
        advanceUntilIdle()

        state = vm.uiState.value as InboxUiState.Success
        assertEquals(2, state.pendingFeedbackQueue.size)
        assertEquals(listOf(email), state.emails)
    }

    @Test fun initial_refresh_keeps_minimum_800ms_pacing() = runTest {
        val vm = InboxViewModel(FakeInboxSource(), nowMillis = { 0L })

        runCurrent()
        assertTrue(vm.uiState.value is InboxUiState.Loading)
        advanceTimeBy(799)
        runCurrent()
        assertTrue(vm.uiState.value is InboxUiState.Loading)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.uiState.value is InboxUiState.Success)
    }
}

// ── Fake ────────────────────────────────────────────────────────

class FakeInboxSource(
    private var moveToTrashResult: EmailActionResult = EmailActionResult.Success,
    private var restoreFromTrashResult: EmailActionResult = EmailActionResult.Success,
    private var markAsReadResult: EmailActionResult = EmailActionResult.Success,
    var moveToTrashGate: CompletableDeferred<Unit>? = null,
    var restoreFromTrashGate: CompletableDeferred<Unit>? = null,
    var markAsReadGate: CompletableDeferred<Unit>? = null,
    private val moveToTrashError: Throwable? = null,
    private val restoreFromTrashError: Throwable? = null,
    private val markAsReadError: Throwable? = null,
    private val refreshResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
) : InboxEmailSource {
    var moveToTrashCalls = 0
    var restoreFromTrashCalls = 0
    var markAsReadCalls = 0
    val room = MutableStateFlow<List<Email>>(emptyList())

    override suspend fun moveToTrash(emailId: String): EmailActionResult {
        moveToTrashCalls++
        moveToTrashGate?.await()
        moveToTrashError?.let { throw it }
        return moveToTrashResult
    }

    override suspend fun restoreFromTrash(emailId: String): EmailActionResult {
        restoreFromTrashCalls++
        restoreFromTrashGate?.await()
        restoreFromTrashError?.let { throw it }
        return restoreFromTrashResult
    }

    override suspend fun markAsRead(emailId: String): EmailActionResult {
        markAsReadCalls++
        markAsReadGate?.await()
        markAsReadError?.let { throw it }
        return markAsReadResult
    }

    override suspend fun refreshInbox(pageToken: String?): PaginatedResult<Email> = refreshResult
    override fun observeInbox(): Flow<List<Email>> = room
}
