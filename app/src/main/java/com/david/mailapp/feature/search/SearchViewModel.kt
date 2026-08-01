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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Internal contract for testable email search.
 */
fun interface SearchEmailSource {
    suspend fun search(query: String, pageToken: String?): PaginatedResult<Email>
}

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

    // ── Job tracking ───────────────────────────────────────────

    private var searchJob: Job? = null
    private var paginationJob: Job? = null
    private var currentGeneration = 0L

    private suspend fun performSearch(query: String, myGen: Long) {
        val lease = writeGuard.capture()
        if (lease == null) {
            if (currentGeneration == myGen && _queryFlow.value == query) {
                _uiState.value = SearchUiState.Idle
            }
            return
        }

        try {
            val result = source.search(query, null)
            if (currentGeneration == myGen && _queryFlow.value == query) {
                nextPageToken = if (result.isComplete) result.nextPageToken else null
                val uniqueEmails = deduplicateEmails(result.items)
                val state = if (uniqueEmails.isEmpty()) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Results(
                        emails = uniqueEmails,
                        query = query,
                        nextPageToken = nextPageToken
                    )
                }
                _uiState.value = state

                try {
                    writeGuard.commit(lease) {
                        saveToHistory(query)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save search history for '$query'", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentGeneration == myGen && _queryFlow.value == query) {
                Log.e(TAG, "Search failed for '$query'", e)
                _uiState.value = SearchUiState.Error(
                    reason = e.toUiErrorReason(),
                    query = query
                )
            }
        }
    }

    private fun deduplicateEmails(items: List<Email>): List<Email> {
        val seen = mutableSetOf<String>()
        return items.filter { seen.add(it.id) }
    }

    // ── Public actions ─────────────────────────────────────────

    fun onQueryChange(newQuery: String) {
        if (newQuery == _queryFlow.value) return
        _queryFlow.value = newQuery

        val myGen = ++currentGeneration
        searchJob?.cancel()
        paginationJob?.cancel()
        nextPageToken = null
        isLoadingNextPage = false

        if (newQuery.length < 2) {
            _uiState.value = SearchUiState.Idle
        } else {
            searchJob = viewModelScope.launch {
                try {
                    delay(300)
                    if (currentGeneration == myGen && _queryFlow.value == newQuery) {
                        _uiState.value = SearchUiState.Loading
                        performSearch(newQuery, myGen)
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun loadNextPage() {
        val current = _uiState.value
        val currentQuery = _queryFlow.value
        if (current !is SearchUiState.Results || current.query != currentQuery || current.isLoadingNextPage || isLoadingNextPage) return
        val token = nextPageToken ?: return
        val myGen = currentGeneration

        isLoadingNextPage = true
        _uiState.value = current.copy(isLoadingNextPage = true)

        paginationJob = viewModelScope.launch {
            try {
                val result = source.search(currentQuery, token)
                if (currentGeneration == myGen && _queryFlow.value == currentQuery) {
                    val after = _uiState.value
                    if (after is SearchUiState.Results && after.query == currentQuery) {
                        val newNextPageToken = if (result.isComplete) result.nextPageToken else token
                        nextPageToken = newNextPageToken

                        val existingEmails = after.emails
                        val seenIds = existingEmails.mapTo(mutableSetOf()) { it.id }
                        val newEmails = result.items.filter { seenIds.add(it.id) }

                        _uiState.value = after.copy(
                            emails = existingEmails + newEmails,
                            nextPageToken = newNextPageToken,
                            isLoadingNextPage = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration == myGen && _queryFlow.value == currentQuery) {
                    Log.e(TAG, "loadNextPage failed for '$currentQuery'", e)
                }
            } finally {
                if (currentGeneration == myGen && _queryFlow.value == currentQuery) {
                    isLoadingNextPage = false
                    val after = _uiState.value
                    if (after is SearchUiState.Results && after.query == currentQuery) {
                        _uiState.value = after.copy(isLoadingNextPage = false)
                    }
                }
            }
        }
    }

    fun retry() {
        val current = _queryFlow.value
        if (current.length >= 2) {
            val myGen = ++currentGeneration
            searchJob?.cancel()
            paginationJob?.cancel()
            nextPageToken = null
            isLoadingNextPage = false

            _uiState.value = SearchUiState.Loading
            searchJob = viewModelScope.launch {
                try {
                    performSearch(current, myGen)
                } catch (e: CancellationException) {
                    throw e
                }
            }
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
