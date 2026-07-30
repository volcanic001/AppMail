package com.david.mailapp.feature.trash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.feature.inbox.ActionFeedback
import com.david.mailapp.feature.inbox.ActionFeedbackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val source: TrashEmailSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrashUiState>(TrashUiState.Loading)
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private var nextPageToken: String? = null
    private var isLoadingNextPage = false
    init {
        observeRoom()
        refresh()
    }

    // ── Room observer ────────────────────────────────────────────

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeTrash().collect { emails ->
                _uiState.update { current ->
                    when (current) {
                        is TrashUiState.Loading -> TrashUiState.Success(emails = emails)
                        is TrashUiState.Success -> current.copy(emails = emails)
                        is TrashUiState.Error -> current
                    }
                }
            }
        }
    }

    // ── Refresh / pagination ────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val isManualRefresh = _uiState.value is TrashUiState.Success
            if (isManualRefresh) {
                _uiState.update { current ->
                    if (current is TrashUiState.Success) current.copy(isRefreshing = true) else current
                }
            }
            try {
                if (isManualRefresh) delay(800)
                nextPageToken = null
                val result = source.refreshTrash(null)
                if (result.isComplete) nextPageToken = result.nextPageToken
                _uiState.update { current ->
                    if (current is TrashUiState.Success) current.copy(isRefreshing = false) else current
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("TrashVM", "refresh failed", e)
                _uiState.update { current ->
                    if (current is TrashUiState.Success) current.copy(isRefreshing = false)
                    else TrashUiState.Error(e.toUiErrorReason())
                }
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingNextPage || nextPageToken == null) return
        isLoadingNextPage = true
        viewModelScope.launch {
            _uiState.update { current ->
                if (current is TrashUiState.Success) current.copy(isLoadingNextPage = true) else current
            }
            try {
                val token = nextPageToken
                val result = source.refreshTrash(token)
                if (result.isComplete) nextPageToken = result.nextPageToken
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("TrashVM", "loadNextPage failed", e) }
            finally {
                isLoadingNextPage = false
                _uiState.update { current ->
                    if (current is TrashUiState.Success) current.copy(isLoadingNextPage = false) else current
                }
            }
        }
    }

    // ── Actions (block + enqueue + release) ──────────────────────

    fun deletePermanently(emailId: String) {
        if (!guardAction(emailId)) return
        viewModelScope.launch {
            try {
                when (val r = source.deletePermanently(emailId)) {
                    is EmailActionResult.Success -> enqueueFeedback(ActionFeedback.DeletedPermanently(emailId))
                    is EmailActionResult.Failure -> enqueueFeedback(ActionFeedback.Failure(r.reason))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { enqueueFeedback(ActionFeedback.Failure(e.toUiErrorReason())) }
            finally { releaseAction(emailId) }
        }
    }

    fun restoreToInbox(emailId: String) {
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

    fun consumeFeedback(feedbackId: ActionFeedbackId) {
        _uiState.update { current ->
            if (current is TrashUiState.Success) current.consumeFeedback(feedbackId) else current
        }
    }

    // ── Guard / enqueue / release ────────────────────────────────

    private fun guardAction(emailId: String): Boolean {
        while (true) {
            val current = _uiState.value
            if (current !is TrashUiState.Success) return false
            if (emailId in current.activeActionEmailIds) return false
            if (_uiState.compareAndSet(current, current.withActive(emailId))) return true
        }
    }

    private fun enqueueFeedback(feedback: ActionFeedback) {
        _uiState.update { current ->
            if (current is TrashUiState.Success) current.withFeedback(feedback) else current
        }
    }

    private fun releaseAction(emailId: String) {
        _uiState.update { current ->
            if (current is TrashUiState.Success) current.withoutActive(emailId) else current
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(private val repository: EmailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
                return TrashViewModel(RepositoryTrashEmailSource(repository)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
