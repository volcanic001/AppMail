package com.david.mailapp.feature.trash

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

    private fun observeRoom() {
        viewModelScope.launch {
            source.observeTrash().collect { emails ->
                val current = _uiState.value
                when (current) {
                    is TrashUiState.Loading -> {
                        _uiState.value = TrashUiState.Success(emails = emails)
                    }
                    is TrashUiState.Success -> {
                        _uiState.value = current.copy(emails = emails)
                    }
                    is TrashUiState.Error -> { /* keep error visible */ }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _uiState.value
            val isManualRefresh = current is TrashUiState.Success
            if (isManualRefresh) {
                _uiState.value = current.copy(isRefreshing = true)
            }
            try {
                if (isManualRefresh) delay(800)
                nextPageToken = source.refreshTrash(null).nextPageToken
                val after = _uiState.value
                if (after is TrashUiState.Success) {
                    _uiState.value = after.copy(isRefreshing = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TrashVM", "refresh failed", e)
                val after = _uiState.value
                if (after is TrashUiState.Success) {
                    _uiState.value = after.copy(isRefreshing = false)
                } else {
                    _uiState.value = TrashUiState.Error(e.toUiErrorReason())
                }
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingNextPage || nextPageToken == null) return
        isLoadingNextPage = true
        viewModelScope.launch {
            val current = _uiState.value
            if (current is TrashUiState.Success) {
                _uiState.value = current.copy(isLoadingNextPage = true)
            }
            try {
                nextPageToken = source.refreshTrash(nextPageToken).nextPageToken
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TrashVM", "loadNextPage failed", e)
            } finally {
                isLoadingNextPage = false
                val after = _uiState.value
                if (after is TrashUiState.Success) {
                    _uiState.value = after.copy(isLoadingNextPage = false)
                }
            }
        }
    }

    fun deletePermanently(emailId: String) {
        viewModelScope.launch {
            source.deletePermanently(emailId)
        }
    }

    fun restoreToInbox(emailId: String) {
        viewModelScope.launch {
            source.restoreFromTrash(emailId)
        }
    }

    class Factory(
        private val repository: EmailRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
                return TrashViewModel(RepositoryTrashEmailSource(repository)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
