package com.david.mailapp.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.data.repository.EmailRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(
    private val repository: EmailRepository
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
            repository.getInbox().collect { emails ->
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
                val start = System.currentTimeMillis()
                val result = repository.refreshInbox(null)
                nextPageToken = result.nextPageToken
                
                val elapsed = System.currentTimeMillis() - start
                if (elapsed < 800) {
                    delay(800 - elapsed)
                }
                
                isInitialRefresh = false
                mergeRefreshSuccess(result.items)
            } catch (e: Exception) {
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
                nextPageToken = repository.refreshInbox(token).nextPageToken
            } catch (_: Exception) {
                // silently fail — emails already visible from cache
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
            repository.moveToTrash(emailId)
        }
    }

    fun undoMoveToTrash(emailId: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(emailId)
        }
    }

    fun markAsRead(emailId: String) {
        viewModelScope.launch {
            repository.markAsRead(emailId)
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
            _uiState.value = InboxUiState.Error(
                e.message ?: "Something went wrong"
            )
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(
        private val repository: EmailRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
                return InboxViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
