package com.david.mailapp.feature.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboxViewModel(
    private val source: InboxEmailSource,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var nextPageToken: String? = null
    private var isLoadingNextPage = false
    private var isInitialRefresh = true
    private var lastSeenEmails: List<com.david.mailapp.domain.model.Email>? = null

    private var refreshJob: Job? = null
    private var paginationJob: Job? = null
    private var currentGeneration = 0L
    private val undoEmailSnapshots = mutableMapOf<String, Email>()
    private val trashOperationCompletions = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val restoredToInboxIds = mutableSetOf<String>()

    /**
     * Time window in which consecutive trash swipes are grouped into a
     * single Snackbar.  Each new swipe within this window extends the timer.
     */
    internal val BATCH_WINDOW_MS = 1_500L

    /** Job that closes the active batch after [BATCH_WINDOW_MS] of inactivity. */
    private var batchWindowJob: Job? = null

    init {
        observeRoom()
        refresh()
    }

    // ── Room observer ────────────────────────────────────────────

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeInbox().collect { emails ->
                lastSeenEmails = emails
                val persistedIds = emails.mapTo(mutableSetOf()) { it.id }
                val confirmedRestoreIds = restoredToInboxIds.filterTo(mutableSetOf()) {
                    it in persistedIds
                }
                restoredToInboxIds.removeAll(confirmedRestoreIds)
                _uiState.update { current ->
                    when (current) {
                        is InboxUiState.Loading -> {
                            if (!isInitialRefresh || emails.isNotEmpty()) {
                                InboxUiState.Success(emails = emails, isRefreshing = isInitialRefresh)
                            } else current
                        }
                        is InboxUiState.Success -> current.copy(
                            emails = emails,
                            pendingOptimisticRemovalIds = current.pendingOptimisticRemovalIds
                                .filterTo(mutableSetOf()) { pendingId ->
                                    pendingId in persistedIds
                                },
                            optimisticRestoredEmails =
                                current.optimisticRestoredEmails - confirmedRestoreIds
                        )
                        is InboxUiState.Error -> current
                    }
                }
            }
        }
    }

    // ── Refresh / pagination ────────────────────────────────────

    fun refresh() {
        val myGen = ++currentGeneration
        refreshJob?.cancel()
        paginationJob?.cancel()
        nextPageToken = null
        isLoadingNextPage = false

        val isManualRefresh = _uiState.value is InboxUiState.Success
        if (isManualRefresh) {
            _uiState.update { current ->
                if (current is InboxUiState.Success) {
                    current.copy(isRefreshing = true, isLoadingNextPage = false)
                } else current
            }
        }

        refreshJob = viewModelScope.launch {
            try {
                val start = nowMillis()
                val result = source.refreshInbox(null)
                val elapsed = nowMillis() - start
                if (elapsed < 800) {
                    delay(800 - elapsed)
                }
                if (currentGeneration == myGen) {
                    if (result.isComplete) {
                        nextPageToken = result.nextPageToken
                    }
                    isInitialRefresh = false
                    mergeRefreshSuccess(result.items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration == myGen) {
                    Log.e("InboxVM", "refresh failed", e)
                    isInitialRefresh = false
                    mergeRefreshError(e)
                }
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingNextPage) return
        val token = nextPageToken ?: return

        val myGen = currentGeneration
        isLoadingNextPage = true

        _uiState.update { current ->
            if (current is InboxUiState.Success) current.copy(isLoadingNextPage = true) else current
        }

        paginationJob = viewModelScope.launch {
            try {
                val result = source.refreshInbox(token)
                if (currentGeneration == myGen && token == nextPageToken) {
                    if (result.isComplete) {
                        nextPageToken = result.nextPageToken
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration == myGen && token == nextPageToken) {
                    Log.e("InboxVM", "loadNextPage failed", e)
                }
            } finally {
                if (currentGeneration == myGen) {
                    isLoadingNextPage = false
                    _uiState.update { current ->
                        if (current is InboxUiState.Success) current.copy(isLoadingNextPage = false) else current
                    }
                }
            }
        }
    }

    // ── Actions ──────────────────────────────────────────────────

    /**
     * Optimistic trash: removes the email from the visible list immediately,
     * groups it into the active [TrashBatch] (or starts a new one), shows an
     * immediate Snackbar, and fires the remote operation in the background.
     *
     * On remote success: silent — the optimistic removal is confirmed.
     * On remote failure: rolls back the optimistic removal so the email
     *   reappears and shows a [ActionFeedback.TrashFailure] Snackbar.
     */
    fun moveToTrash(emailId: String) {
        if (!guardAction(emailId)) return

        (_uiState.value as? InboxUiState.Success)
            ?.visibleEmails
            ?.firstOrNull { it.id == emailId }
            ?.let { undoEmailSnapshots[emailId] = it }

        val operationCompletion = CompletableDeferred<Boolean>()
        trashOperationCompletions[emailId] = operationCompletion

        // 1. Optimistic removal: hide immediately from visible list
        val now = nowMillis()
        val newBatch = _uiState.updateAndGetBatch(emailId, now)

        // 2. Show / update the batch Snackbar immediately (before remote call)
        showBatchSnackbar(newBatch)

        // 3. (Re)start batch-window timer
        batchWindowJob?.cancel()
        batchWindowJob = viewModelScope.launch {
            delay(BATCH_WINDOW_MS)
            // Window expired: seal the batch so the next swipe opens a fresh one.
            _uiState.update { current ->
                if (current is InboxUiState.Success) current.copy(activeBatch = null) else current
            }
        }

        // 4. Fire remote operation
        viewModelScope.launch {
            var moveSucceeded = false
            try {
                when (source.moveToTrash(emailId)) {
                    is EmailActionResult.Success -> {
                        moveSucceeded = true
                        // Remote success: silent. Room will update emails list.
                        // Keep the optimistic removal until the Room observer has
                        // published the folder change. Removing it while the old
                        // Room snapshot still contains the email would briefly
                        // reinsert the row into visibleEmails.
                        _uiState.update { current ->
                            if (current is InboxUiState.Success &&
                                current.emails.none { it.id == emailId }
                            ) {
                                current.withoutOptimisticRemoval(emailId)
                            } else current
                        }
                    }
                    is EmailActionResult.Failure -> {
                        // Remote failure: roll back → email reappears
                        _uiState.update { current ->
                            if (current is InboxUiState.Success)
                                current.withoutOptimisticRemoval(emailId)
                                    .withFeedback(ActionFeedback.TrashFailure(failedCount = 1))
                            else current
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Roll back the optimistic removal so the email reappears.
                // No user-visible error on cancellation.
                _uiState.update { current ->
                    if (current is InboxUiState.Success) current.withoutOptimisticRemoval(emailId)
                    else current
                }
                throw e
            } catch (e: Exception) {
                _uiState.update { current ->
                    if (current is InboxUiState.Success)
                        current.withoutOptimisticRemoval(emailId)
                            .withFeedback(ActionFeedback.TrashFailure(failedCount = 1))
                    else current
                }
            } finally {
                releaseAction(emailId)
                operationCompletion.complete(moveSucceeded)
            }
        }
    }

    /**
     * Undoes an entire trash batch by restoring all emails in [emailIds].
     *
     * Each email is guarded individually (duplicate calls are blocked).
     * Emails whose remote restore fails are reported in a single Failure
     * Snackbar; the rest are silently confirmed once Room updates.
     */
    fun undoMoveToTrash(emailIds: List<String>) {
        if (emailIds.isEmpty()) return

        val snapshots = emailIds.mapNotNull(undoEmailSnapshots::get)
        val moveCompletions = emailIds.associateWith(trashOperationCompletions::get)

        // Undo is optimistic too: reveal the retained rows immediately. Room
        // remains the source of truth and confirms each restore afterward.
        _uiState.update { current ->
            if (current !is InboxUiState.Success) return@update current
            current
                .withoutOptimisticRemovals(emailIds)
                .withOptimisticRestores(snapshots)
        }

        var failedCount = 0
        var completedCount = 0
        val total = emailIds.size

        for (emailId in emailIds) {
            viewModelScope.launch {
                val moveSucceeded = moveCompletions[emailId]?.await() ?: true
                if (!moveSucceeded) {
                    _uiState.update { current ->
                        if (current is InboxUiState.Success) {
                            current.withoutOptimisticRestore(emailId)
                        } else current
                    }
                    completedCount++
                    if (completedCount == total && failedCount > 0) reportUndoFailure(failedCount)
                    return@launch
                }

                // If Undo was tapped while the move was still running, awaiting
                // it above preserves the required remote ordering.
                if (!guardAction(emailId)) {
                    _uiState.update { current ->
                        if (current is InboxUiState.Success) {
                            current.withoutOptimisticRestore(emailId)
                        } else current
                    }
                    completedCount++
                    if (completedCount == total && failedCount > 0) reportUndoFailure(failedCount)
                    return@launch
                }

                try {
                    when (source.restoreFromTrash(emailId)) {
                        is EmailActionResult.Success -> {
                            val hasOptimisticRow =
                                (_uiState.value as? InboxUiState.Success)
                                    ?.optimisticRestoredEmails
                                    ?.containsKey(emailId) == true
                            if (hasOptimisticRow) restoredToInboxIds += emailId
                            _uiState.update { current ->
                                if (current is InboxUiState.Success) {
                                    current.withoutOptimisticRemoval(emailId)
                                        .withFeedback(ActionFeedback.RestoredToInbox(emailId))
                                } else current
                            }
                        }
                        is EmailActionResult.Failure -> {
                            failedCount++
                            _uiState.update { current ->
                                if (current is InboxUiState.Success)
                                    current.withoutOptimisticRemoval(emailId)
                                        .withoutOptimisticRestore(emailId)
                                else current
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    _uiState.update { current ->
                        if (current is InboxUiState.Success) {
                            current.withoutOptimisticRestore(emailId)
                        } else current
                    }
                    throw e
                } catch (e: Exception) {
                    failedCount++
                    _uiState.update { current ->
                        if (current is InboxUiState.Success)
                            current.withoutOptimisticRemoval(emailId)
                                .withoutOptimisticRestore(emailId)
                        else current
                    }
                } finally {
                    releaseAction(emailId)
                    completedCount++
                    if (completedCount == total && failedCount > 0) {
                        reportUndoFailure(failedCount)
                    }
                }
            }
        }
    }

    fun markAsRead(emailId: String) {
        if (!guardAction(emailId)) return
        viewModelScope.launch {
            try {
                when (val r = source.markAsRead(emailId)) {
                    is EmailActionResult.Success -> {} // silent success
                    is EmailActionResult.Failure -> enqueueFeedback(ActionFeedback.Failure(r.reason))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason())) }
            finally { releaseAction(emailId) }
        }
    }

    fun consumeFeedback(feedbackId: ActionFeedbackId) {
        var consumedTrashIds: List<String> = emptyList()
        _uiState.update { current ->
            if (current is InboxUiState.Success) {
                consumedTrashIds = current.pendingFeedbackQueue
                    .filterIsInstance<ActionFeedback.MovedToTrashBatch>()
                    .firstOrNull { it.id == feedbackId }
                    ?.emailIds
                    .orEmpty()
                current.consumeFeedback(feedbackId)
            } else current
        }
        consumedTrashIds.forEach { emailId ->
            undoEmailSnapshots.remove(emailId)
            trashOperationCompletions.remove(emailId)
        }
    }

    // ── Batch helpers ─────────────────────────────────────────────

    /**
     * Atomically adds [emailId] to the optimistic removal set and to the active
     * batch (or creates a new batch), returning the resulting batch.
     */
    private fun MutableStateFlow<InboxUiState>.updateAndGetBatch(
        emailId: String,
        now: Long
    ): TrashBatch {
        var result: TrashBatch? = null
        update { current ->
            if (current !is InboxUiState.Success) return@update current
            val existing = current.activeBatch
            val newBatch = when {
                existing == null -> TrashBatch(listOf(emailId), now)
                else -> existing.copy(
                    emailIds = existing.emailIds + emailId,
                    lastSwipeAtMs = now
                )
            }
            result = newBatch
            current
                .withOptimisticRemoval(emailId)
                .copy(activeBatch = newBatch)
        }
        return result!!
    }

    /**
     * Replaces (or adds) the batch feedback in the queue so the Snackbar
     * updates in place rather than queuing a new one.
     */
    private fun showBatchSnackbar(batch: TrashBatch) {
        _uiState.update { current ->
            if (current !is InboxUiState.Success) return@update current
            val newFeedback = ActionFeedback.MovedToTrashBatch(
                emailIds = batch.emailIds,
                count = batch.emailIds.size,
                id = batch.feedbackId  // same ID → same Snackbar slot
            )
            // Replace any existing feedback with the same batch feedbackId so we
            // update the count rather than queue a brand-new Snackbar.
            val filteredQueue = current.pendingFeedbackQueue.filterNot { it.id == batch.feedbackId }
            current.copy(pendingFeedbackQueue = filteredQueue + newFeedback)
        }
    }

    private fun reportUndoFailure(failedCount: Int) {
        _uiState.update { current ->
            if (current is InboxUiState.Success)
                current.withFeedback(ActionFeedback.TrashFailure(failedCount))
            else current
        }
    }

    // ── Guard / enqueue / release ────────────────────────────────

    private fun guardAction(emailId: String): Boolean {
        while (true) {
            val current = _uiState.value
            if (current !is InboxUiState.Success) return false
            if (emailId in current.activeActionEmailIds) return false
            if (_uiState.compareAndSet(current, current.withActive(emailId))) return true
        }
    }

    private fun enqueueFeedback(feedback: ActionFeedback) {
        _uiState.update { current ->
            if (current is InboxUiState.Success) current.withFeedback(feedback) else current
        }
    }

    private fun releaseAction(emailId: String) {
        _uiState.update { current ->
            if (current is InboxUiState.Success) current.withoutActive(emailId) else current
        }
    }

    // ── Merge helpers ────────────────────────────────────────────

    private fun mergeRefreshSuccess(fetchedRemoteEmails: List<com.david.mailapp.domain.model.Email>) {
        _uiState.update { current ->
            when (current) {
                is InboxUiState.Success -> current.copy(
                    emails = if (current.emails.isEmpty() && fetchedRemoteEmails.isNotEmpty()) {
                        fetchedRemoteEmails
                    } else current.emails,
                    isRefreshing = false
                )
                is InboxUiState.Loading -> {
                    val emailsToShow = if (!lastSeenEmails.isNullOrEmpty()) lastSeenEmails!! else fetchedRemoteEmails
                    InboxUiState.Success(emails = emailsToShow, isRefreshing = false)
                }
                is InboxUiState.Error -> current
            }
        }
    }

    private fun mergeRefreshError(e: Exception) {
        _uiState.update { current ->
            if (current is InboxUiState.Success) {
                current.copy(isRefreshing = false)
            } else {
                InboxUiState.Error(e.toUiErrorReason())
            }
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(private val repository: EmailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
                return InboxViewModel(RepositoryInboxEmailSource(repository)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
