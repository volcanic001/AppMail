package com.david.mailapp.feature.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(
    private val source: InboxEmailSource
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

    // ── Room observer (always runs, drives emails in state) ─────

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeInbox().collect { emails ->
                lastSeenEmails = emails
                val current = _uiState.value
                when (current) {
                    is InboxUiState.Loading -> {
                        if (!isInitialRefresh || emails.isNotEmpty()) {
                            _uiState.value = InboxUiState.Success(
                                emails = emails,
                                isRefreshing = isInitialRefresh
                            )
                        }
                    }
                    is InboxUiState.Success -> {
                        _uiState.value = current.copy(emails = emails)
                    }
                    is InboxUiState.Error -> {
                        // keep error state visible — user must retry
                    }
                }
            }
        }
    }

    // ── Public actions ───────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val current = _uiState.value
            val isManualRefresh = current is InboxUiState.Success
            if (isManualRefresh) {
                _uiState.value = current.copy(isRefreshing = true)
            }

            try {
                // Refresh restarts the paginated window — discard old token
                nextPageToken = null
                val start = System.currentTimeMillis()
                val result = source.refreshInbox(null)
                if (result.isComplete) {
                    nextPageToken = result.nextPageToken
                }

                val elapsed = System.currentTimeMillis() - start
                if (elapsed < 800) {
                    delay(800 - elapsed)
                }

                isInitialRefresh = false
                mergeRefreshSuccess(result.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
            val current = _uiState.value
            if (current is InboxUiState.Success) {
                _uiState.value = current.copy(isLoadingNextPage = true)
            }

            try {
                val token = nextPageToken
                val result = source.refreshInbox(token)
                if (result.isComplete) {
                    nextPageToken = result.nextPageToken
                } // else: keep existing token for retry
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("InboxVM", "loadNextPage failed", e)
            } finally {
                isLoadingNextPage = false
                val after = _uiState.value
                if (after is InboxUiState.Success) {
                    _uiState.value = after.copy(isLoadingNextPage = false)
                }
            }
        }
    }

    fun moveToTrash(emailId: String) {
        viewModelScope.launch {
            source.moveToTrash(emailId)
        }
    }

    fun undoMoveToTrash(emailId: String) {
        viewModelScope.launch {
            source.restoreFromTrash(emailId)
        }
    }

    fun markAsRead(emailId: String) {
        viewModelScope.launch {
            source.markAsRead(emailId)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun mergeRefreshSuccess(fetchedRemoteEmails: List<com.david.mailapp.domain.model.Email>) {
        val current = _uiState.value
        if (current is InboxUiState.Success) {
            _uiState.value = current.copy(
                emails = if (current.emails.isEmpty() && fetchedRemoteEmails.isNotEmpty()) fetchedRemoteEmails else current.emails,
                isRefreshing = false
            )
        } else if (current is InboxUiState.Loading) {
            val emailsToShow = if (!lastSeenEmails.isNullOrEmpty()) lastSeenEmails!! else fetchedRemoteEmails
            _uiState.value = InboxUiState.Success(
                emails = emailsToShow,
                isRefreshing = false
            )
        }
    }

    private fun mergeRefreshError(e: Exception) {
        val current = _uiState.value
        if (current is InboxUiState.Success) {
            _uiState.value = current.copy(isRefreshing = false)
        } else {
            _uiState.value = InboxUiState.Error(e.toUiErrorReason())
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(
        private val repository: EmailRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
                return InboxViewModel(RepositoryInboxEmailSource(repository)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
