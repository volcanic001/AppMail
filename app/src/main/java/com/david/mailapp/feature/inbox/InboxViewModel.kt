package com.david.mailapp.feature.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import kotlinx.coroutines.CancellationException
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

    init {
        observeRoom()
        refresh()
    }

    // ── Room observer ────────────────────────────────────────────

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeInbox().collect { emails ->
                lastSeenEmails = emails
                _uiState.update { current ->
                    when (current) {
                    is InboxUiState.Loading -> {
                        if (!isInitialRefresh || emails.isNotEmpty()) {
                                InboxUiState.Success(emails = emails, isRefreshing = isInitialRefresh)
                            } else current
                    }
                        is InboxUiState.Success -> current.copy(emails = emails)
                        is InboxUiState.Error -> current
                    }
                }
            }
        }
    }

    // ── Refresh / pagination ────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val isManualRefresh = _uiState.value is InboxUiState.Success
            if (isManualRefresh) {
                _uiState.update { current ->
                    if (current is InboxUiState.Success) current.copy(isRefreshing = true) else current
                }
            }

            try {
                // Refresh restarts the paginated window — discard old token
                nextPageToken = null
                val start = nowMillis()
                val result = source.refreshInbox(null)
                if (result.isComplete) {
                    nextPageToken = result.nextPageToken
                }

                val elapsed = nowMillis() - start
                if (elapsed < 800) {
                    delay(800 - elapsed)
                }
                isInitialRefresh = false
                mergeRefreshSuccess(result.items)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("InboxVM", "refresh failed", e)
                isInitialRefresh = false
                mergeRefreshError(e)
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingNextPage || nextPageToken == null) return
        isLoadingNextPage = true
        viewModelScope.launch {
            _uiState.update { current ->
                if (current is InboxUiState.Success) current.copy(isLoadingNextPage = true) else current
            }
            try {
                val token = nextPageToken
                val result = source.refreshInbox(token)
                if (result.isComplete) nextPageToken = result.nextPageToken
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("InboxVM", "loadNextPage failed", e) }
            finally {
                isLoadingNextPage = false
                _uiState.update { current ->
                    if (current is InboxUiState.Success) current.copy(isLoadingNextPage = false) else current
                }
            }
        }
    }

    // ── Actions (block + enqueue + release) ──────────────────────

    fun moveToTrash(emailId: String) {
        if (!guardAction(emailId)) return
        viewModelScope.launch {
            try {
                when (val r = source.moveToTrash(emailId)) {
                    is EmailActionResult.Success -> enqueueFeedback(ActionFeedback.MovedToTrash(emailId))
                    is EmailActionResult.Failure -> enqueueFeedback(ActionFeedback.Failure(r.reason))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason())) }
            finally { releaseAction(emailId) }
        }
    }

    fun undoMoveToTrash(emailId: String) {
        if (!guardAction(emailId)) return
        viewModelScope.launch {
            try {
                when (val r = source.restoreFromTrash(emailId)) {
                    is EmailActionResult.Success -> enqueueFeedback(ActionFeedback.RestoredToInbox(emailId))
                    is EmailActionResult.Failure -> enqueueFeedback(ActionFeedback.Failure(r.reason))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason())) }
            finally { releaseAction(emailId) }
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
        _uiState.update { current ->
            if (current is InboxUiState.Success) current.consumeFeedback(feedbackId) else current
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
