package com.david.mailapp.feature.search

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Internal contract for testable email search.
 */
fun interface SearchEmailSource {
    suspend fun search(query: String, pageToken: String?): PaginatedResult<Email>
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val source: SearchEmailSource,
    private val historyStore: DataStore<Preferences>,
    private val writeGuard: SessionWriteGuard
) : ViewModel() {

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("search_history")
        private const val TAG = "SearchVM"
    }

    // ── Query ──────────────────────────────────────────────────

    private val _queryFlow = MutableStateFlow("")

    /** The current search query text. */
    val query: StateFlow<String> = _queryFlow.asStateFlow()

    // ── Retry signal (bypasses debounce) ───────────────────────

    private val _retryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // ── UI State ───────────────────────────────────────────────

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // ── History ────────────────────────────────────────────────

    val historyFlow = historyStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY] ?: ""
        if (raw.isEmpty()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    // ── Pagination state ───────────────────────────────────────

    private var nextPageToken: String? = null
    private var isLoadingNextPage = false

    // ── Search pipeline ────────────────────────────────────────

    init {
        viewModelScope.launch {
            merge(
                _queryFlow.debounce(300).filter { it.length >= 2 },
                _retryFlow
            )
                .flatMapLatest { query ->
                    Log.d(TAG, "Starting search pipeline for: '$query'")
                    _uiState.value = SearchUiState.Loading

                    flow {
                        val lease = writeGuard.capture()
                        if (lease == null) {
                            emit(SearchUiState.Idle)
                            return@flow
                        }

                        try {
                            val result = source.search(query, null)
                            if (result.isComplete) {
                                nextPageToken = result.nextPageToken
                            }

                            val state = if (result.items.isEmpty()) {
                                SearchUiState.Empty(query)
                            } else {
                                SearchUiState.Results(
                                    emails = result.items,
                                    query = query,
                                    nextPageToken = nextPageToken
                                )
                            }
                            emit(state)

                            // Save to history only on successful search
                            try {
                                writeGuard.commit(lease) {
                                    saveToHistory(query)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save search history for '$query'", e)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Search failed for '$query'", e)
                            emit(
                                SearchUiState.Error(
                                    reason = e.toUiErrorReason(),
                                    query = query
                                )
                            )
                        }
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    // ── Public actions ─────────────────────────────────────────

    fun onQueryChange(newQuery: String) {
        _queryFlow.value = newQuery
        if (newQuery.length < 2) {
            _uiState.value = SearchUiState.Idle
            nextPageToken = null
        }
    }

    fun clearQuery() {
        _queryFlow.value = ""
        _uiState.value = SearchUiState.Idle
        nextPageToken = null
    }

    fun loadNextPage() {
        if (isLoadingNextPage || nextPageToken == null) return
        isLoadingNextPage = true

        val currentQuery = _queryFlow.value
        viewModelScope.launch {
            val current = _uiState.value
            if (current is SearchUiState.Results) {
                _uiState.value = current.copy(isLoadingNextPage = true)
            }

            try {
                val result = source.search(currentQuery, nextPageToken)
                if (result.isComplete) {
                    nextPageToken = result.nextPageToken
                } // else: keep existing token for retry

                val after = _uiState.value
                if (after is SearchUiState.Results) {
                    _uiState.value = after.copy(
                        emails = after.emails + result.items,
                        nextPageToken = nextPageToken,
                        isLoadingNextPage = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadNextPage failed for '$currentQuery'", e)
            } finally {
                isLoadingNextPage = false
                val after = _uiState.value
                if (after is SearchUiState.Results) {
                    _uiState.value = after.copy(isLoadingNextPage = false)
                }
            }
        }
    }

    fun retry() {
        val current = _queryFlow.value
        if (current.length >= 2) {
            _retryFlow.tryEmit(current)
        }
    }

    // ── History ────────────────────────────────────────────────

    private suspend fun saveToHistory(query: String) {
        historyStore.edit { prefs ->
            val existing = (prefs[HISTORY_KEY] ?: "")
                .split("|")
                .filter { it.isNotBlank() }
                .toMutableList()

            // Remove duplicate if exists, then add to front
            existing.removeAll { it.equals(query, ignoreCase = true) }
            existing.add(0, query)

            prefs[HISTORY_KEY] = existing.take(5).joinToString("|")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                val lease = writeGuard.capture() ?: return@launch
                writeGuard.commit(lease) {
                    historyStore.edit { it.remove(HISTORY_KEY) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "clearHistory failed", e)
            }
        }
    }

    // ── Factory ────────────────────────────────────────────────

    class Factory(
        private val repository: EmailRepository,
        private val historyStore: DataStore<Preferences>,
        private val writeGuard: SessionWriteGuard
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                return SearchViewModel(
                    source = SearchEmailSource { query, pageToken ->
                        repository.searchEmails(query, pageToken)
                    },
                    historyStore = historyStore,
                    writeGuard = writeGuard
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
