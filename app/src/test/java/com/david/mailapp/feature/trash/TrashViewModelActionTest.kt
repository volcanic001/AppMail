package com.david.mailapp.feature.trash

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.feature.inbox.ActionFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class TrashViewModelActionTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val email = Email("e1", "t1", "from", "F", "to", "S", "", 1000L,
        false, false, false, emptyList(), EmailFolder.Trash)

    // ── Delete: success → DeletedPermanently ────────────────────

    @Test fun delete_success_enqueues_DeletedPermanently_no_undo() = runTest {
        val src = FakeTrashSource(deleteResult = EmailActionResult.Success)
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.DeletedPermanently })
        assertFalse(state.activeActionEmailIds.contains("e1"))
        assertEquals(1, src.deleteCalls)
    }

    // ── Delete: failure → Failure ───────────────────────────────

    @Test fun delete_failure_enqueues_Failure_not_success() = runTest {
        val src = FakeTrashSource(deleteResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false))
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.Failure })
        assertTrue(state.pendingFeedbackQueue.none { it is ActionFeedback.DeletedPermanently })
    }

    // ── Restore: success → RestoredToInbox ──────────────────────

    @Test fun restore_success_enqueues_RestoredToInbox_no_undo() = runTest {
        val src = FakeTrashSource(restoreResult = EmailActionResult.Success)
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.restoreToInbox("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.pendingFeedbackQueue.any { it is ActionFeedback.RestoredToInbox })
    }

    // ── Block duplicates ────────────────────────────────────────

    @Test fun delete_duplicate_blocked_while_active() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeTrashSource(deleteResult = EmailActionResult.Success, deleteGate = gate)
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("e1")
        advanceUntilIdle()
        vm.deletePermanently("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.activeActionEmailIds.contains("e1"))

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, src.deleteCalls)
    }

    // ── Concurrent different IDs ────────────────────────────────

    @Test fun concurrent_different_ids_both_enqueue() = runTest {
        val deleteGate = CompletableDeferred<Unit>()
        val restoreGate = CompletableDeferred<Unit>()
        val src = FakeTrashSource(
            deleteResult = EmailActionResult.Success,
            restoreResult = EmailActionResult.Success,
            deleteGate = deleteGate,
            restoreGate = restoreGate
        )
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("a")
        vm.restoreToInbox("b")
        runCurrent()

        val active = vm.uiState.value as TrashUiState.Success
        assertEquals(setOf("a", "b"), active.activeActionEmailIds)

        deleteGate.complete(Unit)
        restoreGate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertEquals(2, state.pendingFeedbackQueue.size)
    }

    @Test fun unexpected_exception_enqueues_failure_and_releases_action() = runTest {
        val src = FakeTrashSource(deleteError = IOException("offline"))
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.pendingFeedbackQueue.single() is ActionFeedback.Failure)
        assertFalse("e1" in state.activeActionEmailIds)
    }

    @Test fun cancellation_releases_action_without_feedback() = runTest {
        val src = FakeTrashSource(deleteError = CancellationException("cancelled"))
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.deletePermanently("e1")
        advanceUntilIdle()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue(state.pendingFeedbackQueue.isEmpty())
        assertFalse("e1" in state.activeActionEmailIds)
    }

    @Test fun room_emission_preserves_active_action_and_feedback() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeTrashSource(
            deleteGate = gate,
            restoreResult = EmailActionResult.Failure(UiErrorReason.NO_CONNECTION, false)
        )
        val vm = TrashViewModel(src)
        advanceUntilIdle()

        vm.restoreToInbox("feedback")
        advanceUntilIdle()
        vm.deletePermanently("active")
        runCurrent()
        src.room.value = listOf(email)
        runCurrent()

        val state = vm.uiState.value as TrashUiState.Success
        assertTrue("active" in state.activeActionEmailIds)
        assertEquals(1, state.pendingFeedbackQueue.size)
        assertEquals(listOf(email), state.emails)

        gate.complete(Unit)
        advanceUntilIdle()
    }
}

class FakeTrashSource(
    private var deleteResult: EmailActionResult = EmailActionResult.Success,
    private var restoreResult: EmailActionResult = EmailActionResult.Success,
    var deleteGate: CompletableDeferred<Unit>? = null,
    var restoreGate: CompletableDeferred<Unit>? = null,
    private val deleteError: Throwable? = null,
    private val restoreError: Throwable? = null
) : TrashEmailSource {
    var deleteCalls = 0
    var restoreCalls = 0
    val room = MutableStateFlow<List<Email>>(emptyList())

    override suspend fun deletePermanently(emailId: String): EmailActionResult {
        deleteCalls++
        deleteGate?.await()
        deleteError?.let { throw it }
        return deleteResult
    }
    override suspend fun restoreFromTrash(emailId: String): EmailActionResult {
        restoreCalls++
        restoreGate?.await()
        restoreError?.let { throw it }
        return restoreResult
    }
    override suspend fun refreshTrash(pageToken: String?): PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    override fun observeTrash(): Flow<List<Email>> = room
}
