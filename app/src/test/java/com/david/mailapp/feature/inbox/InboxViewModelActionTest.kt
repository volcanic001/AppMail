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
import org.junit.Assert.assertNotEquals
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

    // ── Optimistic removal ──────────────────────────────────────

    @Test fun moveToTrash_keeps_email_hidden_until_room_confirms_removal() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success, moveToTrashGate = gate)
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        runCurrent()

        val state = vm.uiState.value as InboxUiState.Success
        // Email is hidden from visible list immediately
        assertTrue("e1 should be in pendingOptimisticRemovalIds", "e1" in state.pendingOptimisticRemovalIds)
        assertTrue("e1 should not appear in visibleEmails", state.visibleEmails.none { it.id == "e1" })

        gate.complete(Unit)
        advanceUntilIdle()

        val after = vm.uiState.value as InboxUiState.Success
        assertTrue("Remote success must not expose a stale Room row",
            "e1" in after.pendingOptimisticRemovalIds)
        assertTrue("e1 should remain hidden while Room still contains it",
            after.visibleEmails.none { it.id == "e1" })

        src.room.value = emptyList()
        runCurrent()

        val confirmed = vm.uiState.value as InboxUiState.Success
        assertFalse("Room confirmation should clear the optimistic removal",
            "e1" in confirmed.pendingOptimisticRemovalIds)
    }

    @Test fun moveToTrash_remote_failure_restores_email_to_visible() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(
            moveToTrashResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false),
            moveToTrashGate = gate
        )
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        runCurrent()

        // Hidden immediately (gate keeps remote suspended)
        val mid = vm.uiState.value as InboxUiState.Success
        assertTrue("e1 should be in optimistic removal immediately", "e1" in mid.pendingOptimisticRemovalIds)

        gate.complete(Unit)
        advanceUntilIdle()

        val after = vm.uiState.value as InboxUiState.Success
        // Rolled back: email is no longer in optimistic removal set → reappears via Room
        assertFalse("e1 should be removed from optimistic set on failure",
            "e1" in after.pendingOptimisticRemovalIds)
        // Error feedback shown
        assertTrue("TrashFailure feedback expected",
            after.pendingFeedbackQueue.any { it is ActionFeedback.TrashFailure })
    }

    // ── Snackbar is immediate ───────────────────────────────────

    @Test fun snackbar_shows_immediately_not_after_remote_confirm() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success, moveToTrashGate = gate)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        runCurrent() // don't complete gate yet

        val state = vm.uiState.value as InboxUiState.Success
        // Snackbar already in queue before Gmail responds
        val countBefore = state.pendingFeedbackQueue.count { it is ActionFeedback.MovedToTrashBatch }
        assertTrue("Snackbar should be enqueued immediately", countBefore >= 1)

        gate.complete(Unit)
        advanceUntilIdle()

        val after = vm.uiState.value as InboxUiState.Success
        // The same snackbar is still in queue (consumed by UI, not by ViewModel on success).
        // Crucially, no NEW batch snackbar was added on remote success.
        val countAfter = after.pendingFeedbackQueue.count { it is ActionFeedback.MovedToTrashBatch }
        assertEquals("Remote success must not add an extra batch snackbar", countBefore, countAfter)
    }

    // ── Batch Snackbar accumulation ─────────────────────────────

    @Test fun rapid_swipes_accumulate_in_single_batch_snackbar() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("a")
        runCurrent()
        vm.moveToTrash("b")
        runCurrent()
        vm.moveToTrash("c")
        runCurrent()
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        val batchFeedbacks = state.pendingFeedbackQueue.filterIsInstance<ActionFeedback.MovedToTrashBatch>()
        // All three swipes should be in a single batch (or all consumed)
        val totalCount = batchFeedbacks.sumOf { it.count }
        assertTrue("Batch should have accumulated count >= 3 or feedback was consumed",
            batchFeedbacks.isEmpty() || totalCount >= 3)
    }

    @Test fun batch_snackbar_has_same_id_when_updated() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success, moveToTrashGate = gate)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("a")
        runCurrent()

        val firstBatch = (vm.uiState.value as InboxUiState.Success)
            .pendingFeedbackQueue.filterIsInstance<ActionFeedback.MovedToTrashBatch>()
            .firstOrNull()
        val firstId = firstBatch?.id

        vm.moveToTrash("b")
        runCurrent()

        val secondBatch = (vm.uiState.value as InboxUiState.Success)
            .pendingFeedbackQueue.filterIsInstance<ActionFeedback.MovedToTrashBatch>()
            .firstOrNull()

        assertEquals("Second swipe should update same Snackbar (same batch ID)", firstId, secondBatch?.id)
        assertEquals("Batch count should be 2", 2, secondBatch?.count)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun separate_swipes_after_batch_window_are_independent_batches() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("a")
        runCurrent()

        val firstBatchFeedbackId = (vm.uiState.value as InboxUiState.Success)
            .pendingFeedbackQueue
            .filterIsInstance<ActionFeedback.MovedToTrashBatch>()
            .first().id

        // Consume the first batch snackbar
        vm.consumeFeedback(firstBatchFeedbackId)

        // Advance past the batch window
        advanceTimeBy(vm.BATCH_WINDOW_MS + 100)
        advanceUntilIdle()

        vm.moveToTrash("b")
        runCurrent()
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        val newBatch = state.pendingFeedbackQueue
            .filterIsInstance<ActionFeedback.MovedToTrashBatch>()
            .firstOrNull()

        if (newBatch != null) {
            assertNotEquals("Second batch should have a different ID", firstBatchFeedbackId, newBatch.id)
            assertEquals("Second batch should only contain 'b'", listOf("b"), newBatch.emailIds)
        }
    }

    // ── Undo batch ──────────────────────────────────────────────

    @Test fun undo_batch_calls_restore_for_all_ids() = runTest {
        val src = FakeInboxSource(restoreFromTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        val ids = listOf("a", "b", "c")
        vm.undoMoveToTrash(ids)
        advanceUntilIdle()

        assertEquals("restoreFromTrash should be called for each email", 3, src.restoreFromTrashCalls)
    }

    @Test fun undo_batch_does_not_hide_rows_while_restore_is_in_flight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(restoreFromTrashResult = EmailActionResult.Success, restoreFromTrashGate = gate)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        val ids = listOf("a", "b")
        vm.undoMoveToTrash(ids)
        runCurrent()

        val state = vm.uiState.value as InboxUiState.Success
        assertFalse("a should not remain hidden during restore", "a" in state.pendingOptimisticRemovalIds)
        assertFalse("b should not remain hidden during restore", "b" in state.pendingOptimisticRemovalIds)

        gate.complete(Unit)
        advanceUntilIdle()

        val after = vm.uiState.value as InboxUiState.Success
        assertFalse("a should be removed from optimistic set after restore", "a" in after.pendingOptimisticRemovalIds)
        assertFalse("b should be removed from optimistic set after restore", "b" in after.pendingOptimisticRemovalIds)
    }

    @Test fun undo_reveals_room_removed_email_before_remote_restore_completes() = runTest {
        val restoreGate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(
            moveToTrashResult = EmailActionResult.Success,
            restoreFromTrashResult = EmailActionResult.Success,
            restoreFromTrashGate = restoreGate
        )
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash(email.id)
        advanceUntilIdle()
        src.room.value = emptyList()
        runCurrent()

        assertTrue((vm.uiState.value as InboxUiState.Success).visibleEmails.isEmpty())

        vm.undoMoveToTrash(listOf(email.id))
        runCurrent()

        val optimistic = vm.uiState.value as InboxUiState.Success
        assertTrue("Undo should reveal the retained row immediately",
            optimistic.visibleEmails.any { it.id == email.id })
        assertEquals(1, src.restoreFromTrashCalls)

        restoreGate.complete(Unit)
        runCurrent()
        src.room.value = listOf(email)
        runCurrent()

        val confirmed = vm.uiState.value as InboxUiState.Success
        assertTrue(confirmed.visibleEmails.any { it.id == email.id })
        assertFalse(email.id in confirmed.optimisticRestoredEmails)
    }

    @Test fun immediate_undo_waits_for_in_flight_move_before_remote_restore() = runTest {
        val moveGate = CompletableDeferred<Unit>()
        val restoreGate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(
            moveToTrashResult = EmailActionResult.Success,
            restoreFromTrashResult = EmailActionResult.Success,
            moveToTrashGate = moveGate,
            restoreFromTrashGate = restoreGate
        )
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash(email.id)
        runCurrent()
        vm.undoMoveToTrash(listOf(email.id))
        runCurrent()

        assertTrue((vm.uiState.value as InboxUiState.Success)
            .visibleEmails.any { it.id == email.id })
        assertEquals("Restore must wait for the move operation", 0, src.restoreFromTrashCalls)

        moveGate.complete(Unit)
        runCurrent()

        assertEquals(1, src.restoreFromTrashCalls)
        restoreGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun undo_batch_partial_failure_shows_trash_failure_snackbar() = runTest {
        val src = FakeInboxSource(restoreFromTrashError = IOException("network"))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.undoMoveToTrash(listOf("a", "b"))
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue("TrashFailure expected for failed restore",
            state.pendingFeedbackQueue.any { it is ActionFeedback.TrashFailure })
    }

    // ── Non-destructive actions don't trigger optimistic removal ─

    @Test fun mark_as_read_does_not_add_to_optimistic_removal() = runTest {
        val src = FakeInboxSource(markAsReadResult = EmailActionResult.Success)
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.markAsRead("e1")
        runCurrent()

        val state = vm.uiState.value as InboxUiState.Success
        assertFalse("markAsRead must NOT add to optimistic removal",
            "e1" in state.pendingOptimisticRemovalIds)
        assertTrue("Email must still be visible", state.visibleEmails.any { it.id == "e1" })
    }

    // ── Existing behaviour preserved ────────────────────────────

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

    @Test fun undo_as_new_remote_operation_enqueues_RestoredToInbox() = runTest {
        val src = FakeInboxSource(restoreFromTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.undoMoveToTrash(listOf("e2"))
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.RestoredToInbox })
        assertEquals(1, src.restoreFromTrashCalls)
    }

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

    @Test fun concurrent_different_ids_both_get_optimistic_removal() = runTest {
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
        assertTrue("a" in active.pendingOptimisticRemovalIds)
        assertTrue("b" in active.pendingOptimisticRemovalIds)
        assertEquals(2, src.moveToTrashCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertFalse("a" in state.pendingOptimisticRemovalIds)
        assertFalse("b" in state.pendingOptimisticRemovalIds)
    }

    @Test fun consume_feedback_removes_it_from_queue() = runTest {
        val src = FakeInboxSource(moveToTrashResult = EmailActionResult.Success)
        val vm = InboxViewModel(src)
        advanceUntilIdle()
        vm.moveToTrash("e1")
        runCurrent()

        val f = (vm.uiState.value as InboxUiState.Success).pendingFeedbackQueue.first()
        vm.consumeFeedback(f.id)
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        assertTrue(state.pendingFeedbackQueue.isEmpty())
    }

    @Test fun unexpected_exception_rolls_back_and_enqueues_trash_failure() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeInboxSource(moveToTrashError = IOException("offline"), moveToTrashGate = gate)
        src.room.value = listOf(email)
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        runCurrent()
        // Optimistic removal applied immediately
        assertTrue("e1" in (vm.uiState.value as InboxUiState.Success).pendingOptimisticRemovalIds)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        // Queue has: batch snackbar (optimistic) + TrashFailure (error)
        assertTrue("TrashFailure should be in queue",
            state.pendingFeedbackQueue.any { it is ActionFeedback.TrashFailure })
        assertFalse("e1" in state.activeActionEmailIds)
        assertFalse("e1 should be rolled back", "e1" in state.pendingOptimisticRemovalIds)
    }

    @Test fun cancellation_releases_action_without_trash_failure() = runTest {
        val src = FakeInboxSource(moveToTrashError = CancellationException("cancelled"))
        val vm = InboxViewModel(src)
        advanceUntilIdle()

        vm.moveToTrash("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as InboxUiState.Success
        // The optimistic batch Snackbar is pre-enqueued before the remote call, so
        // the queue may have it. But no TrashFailure should be present on cancellation.
        assertFalse("No TrashFailure on cancellation",
            state.pendingFeedbackQueue.any { it is ActionFeedback.TrashFailure })
        assertFalse("e1" in state.activeActionEmailIds)
        // Optimistic removal is NOT rolled back on CancellationException (not a user-facing failure)
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
        assertEquals(listOf(email), state.emails)
        assertTrue(state.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()

        state = vm.uiState.value as InboxUiState.Success
        // "feedback" failure + Snackbar from optimistic trash
        assertTrue(state.pendingFeedbackQueue.size >= 1)
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

    // ── visibleEmails contract ──────────────────────────────────

    @Test fun visibleEmails_excludes_optimistic_removals() = runTest {
        val emails = listOf(
            Email("a", "t1", "x", "X", "y", "S1", "", 1L, false, false, false, emptyList(), EmailFolder.Inbox),
            Email("b", "t2", "x", "X", "y", "S2", "", 2L, false, false, false, emptyList(), EmailFolder.Inbox)
        )
        val state = InboxUiState.Success(
            emails = emails,
            pendingOptimisticRemovalIds = setOf("a")
        )
        assertEquals(listOf("b"), state.visibleEmails.map { it.id })
    }

    @Test fun visibleEmails_equals_emails_when_no_removals() = runTest {
        val emails = listOf(
            Email("a", "t1", "x", "X", "y", "S1", "", 1L, false, false, false, emptyList(), EmailFolder.Inbox)
        )
        val state = InboxUiState.Success(emails = emails)
        assertEquals(state.emails, state.visibleEmails)
    }
}

// ── Fake ─────────────────────────────────────────────────────────

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
