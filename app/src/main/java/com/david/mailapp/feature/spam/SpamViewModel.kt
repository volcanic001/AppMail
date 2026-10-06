package com.david.mailapp.feature.spam

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.feature.inbox.ActionFeedback
import com.david.mailapp.feature.inbox.ActionFeedbackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpamViewModel(
    private val source: SpamEmailSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpamUiState>(SpamUiState.Loading)
    val uiState: StateFlow<SpamUiState> = _uiState.asStateFlow()

    private var nextPageToken: String? = null
    private var isLoadingNextPage = false

    private var refreshJob: Job? = null
    private var paginationJob: Job? = null
    private var currentGeneration = 0L

    /**
     * True until the first refresh completes. While it holds, an empty Room
     * emission must NOT flip the screen to the "spam is empty" state — the
     * cache simply has not loaded yet. We keep showing the loading skeleton
     * until the server confirms the real contents. Mirrors InboxViewModel.
     */
    private var isInitialRefresh = true

    init {
        observeRoom()
        refresh()
    }

    // ── Room observer ────────────────────────────────────────────

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeSpam().collect { emails ->
                val persistedIds = emails.mapTo(mutableSetOf()) { it.id }
                _uiState.update { current ->
                    when (current) {
                        is SpamUiState.Loading -> {
                            if (!isInitialRefresh || emails.isNotEmpty()) {
                                SpamUiState.Success(emails = emails, isRefreshing = isInitialRefresh)
                            } else current
                        }
                        is SpamUiState.Success -> current.copy(
                            emails = emails,
                            // Drop optimistic removals once Room no longer holds
                            // the email (the action's effect has landed).
                            pendingOptimisticRemovalIds = current.pendingOptimisticRemovalIds
                                .filterTo(mutableSetOf()) { it in persistedIds }
                        )
                        is SpamUiState.Error -> current
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

        val isManualRefresh = _uiState.value is SpamUiState.Success
        if (isManualRefresh) {
            _uiState.update { current ->
                if (current is SpamUiState.Success) {
                    current.copy(isRefreshing = true, isLoadingNextPage = false)
                } else current
            }
        }

        refreshJob = viewModelScope.launch {
            try {
                if (isManualRefresh) delay(800)
                val result = source.refreshSpam(null)
                if (currentGeneration == myGen) {
                    if (result.isComplete) {
                        nextPageToken = result.nextPageToken
                    }
                    isInitialRefresh = false
                    _uiState.update { current ->
                        when (current) {
                            is SpamUiState.Success -> current.copy(isRefreshing = false)
                            // Spam is genuinely empty: the cache never produced a
                            // non-empty emission, so close out the initial load here.
                            is SpamUiState.Loading ->
                                SpamUiState.Success(emails = result.items, isRefreshing = false)
                            is SpamUiState.Error -> current
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration == myGen) {
                    Log.e("SpamVM", "refresh failed", e)
                    isInitialRefresh = false
                    _uiState.update { current ->
                        if (current is SpamUiState.Success) {
                            current.copy(isRefreshing = false)
                        } else {
                            SpamUiState.Error(e.toUiErrorReason())
                        }
                    }
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
            if (current is SpamUiState.Success) current.copy(isLoadingNextPage = true) else current
        }

        paginationJob = viewModelScope.launch {
            try {
                val result = source.refreshSpam(token)
                if (currentGeneration == myGen && token == nextPageToken) {
                    if (result.isComplete) {
                        nextPageToken = result.nextPageToken
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration == myGen && token == nextPageToken) {
                    Log.e("SpamVM", "loadNextPage failed", e)
                }
            } finally {
                if (currentGeneration == myGen) {
                    isLoadingNextPage = false
                    _uiState.update { current ->
                        if (current is SpamUiState.Success) current.copy(isLoadingNextPage = false) else current
                    }
                }
            }
        }
    }

    // ── Actions (block + enqueue + release) ──────────────────────

    /** "No es spam": moves the email back to the inbox. */
    fun markNotSpam(emailId: String) {
        if (!guardAction(emailId)) return
        optimisticallyRemove(emailId)
        viewModelScope.launch {
            try {
                when (val r = source.markNotSpam(emailId)) {
                    is EmailActionResult.Success -> enqueueFeedback(ActionFeedback.MarkedNotSpam(emailId))
                    is EmailActionResult.Failure -> {
                        rollbackOptimisticRemoval(emailId)
                        enqueueFeedback(ActionFeedback.Failure(r.reason))
                    }
                }
            } catch (e: CancellationException) { rollbackOptimisticRemoval(emailId); throw e }
            catch (e: Exception) {
                rollbackOptimisticRemoval(emailId)
                enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason()))
            }
            finally { releaseAction(emailId) }
        }
    }

    /** Deletes a spam email by moving it to the trash. */
    fun deleteToTrash(emailId: String) {
        if (!guardAction(emailId)) return
        optimisticallyRemove(emailId)
        viewModelScope.launch {
            try {
                when (val r = source.deleteToTrash(emailId)) {
                    is EmailActionResult.Success ->
                        enqueueFeedback(ActionFeedback.MovedToTrashBatch(listOf(emailId)))
                    is EmailActionResult.Failure -> {
                        rollbackOptimisticRemoval(emailId)
                        enqueueFeedback(ActionFeedback.Failure(r.reason))
                    }
                }
            } catch (e: CancellationException) { rollbackOptimisticRemoval(emailId); throw e }
            catch (e: Exception) {
                rollbackOptimisticRemoval(emailId)
                enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason()))
            }
            finally { releaseAction(emailId) }
        }
    }

    fun consumeFeedback(feedbackId: ActionFeedbackId) {
        _uiState.update { current ->
            if (current is SpamUiState.Success) current.consumeFeedback(feedbackId) else current
        }
    }

    // ── Guard / enqueue / release ────────────────────────────────

    private fun guardAction(emailId: String): Boolean {
        while (true) {
            val current = _uiState.value
            if (current !is SpamUiState.Success) return false
            if (emailId in current.activeActionEmailIds) return false
            if (_uiState.compareAndSet(current, current.withActive(emailId))) return true
        }
    }

    /** Hide the row immediately so the swipe feels instant. */
    private fun optimisticallyRemove(emailId: String) {
        _uiState.update { current ->
            if (current is SpamUiState.Success) current.withOptimisticRemoval(emailId) else current
        }
    }

    /** Bring the row back when the background operation fails or is cancelled. */
    private fun rollbackOptimisticRemoval(emailId: String) {
        _uiState.update { current ->
            if (current is SpamUiState.Success) current.withoutOptimisticRemoval(emailId) else current
        }
    }

    private fun enqueueFeedback(feedback: ActionFeedback) {
        _uiState.update { current ->
            if (current is SpamUiState.Success) current.withFeedback(feedback) else current
        }
    }

    private fun releaseAction(emailId: String) {
        _uiState.update { current ->
            if (current is SpamUiState.Success) current.withoutActive(emailId) else current
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(private val repository: EmailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SpamViewModel::class.java)) {
                return SpamViewModel(RepositorySpamEmailSource(repository)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
