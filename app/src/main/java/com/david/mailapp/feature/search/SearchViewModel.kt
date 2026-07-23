package com.david.mailapp.feature.search

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: EmailRepository,
    private val historyStore: DataStore<Preferences>,
    private val writeGuard: SessionWriteGuard
) : ViewModel() {

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("search_history")
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

    // ── Search pipeline ────────────────────────────────────────

    init {
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .filter {
                    Log.d("SearchDebug", "[ViewModel] Filter check: '$it' (len: ${it.length})")
                    it.length >= 2
                }
                .flatMapLatest { query ->
                    Log.d("SearchDebug", "[ViewModel] Starting search pipeline for: '$query'")
                    _uiState.value = SearchUiState.Loading

                    flow {
                        val lease = writeGuard.capture()
                        if (lease == null) {
                            emit(SearchUiState.Idle)
                            return@flow
                        }

                        try {
                            Log.d("SearchDebug", "[ViewModel] Calling repository.searchEmails('$query')...")
                            val result = repository.searchEmails(query)
                            Log.d("SearchDebug", "[ViewModel] Repo returned ${result.items.size} items. nextPageToken: ${result.nextPageToken}")
                            nextPageToken = result.nextPageToken

                            val state = if (result.items.isEmpty()) {
                                Log.d("SearchDebug", "[ViewModel] State -> Empty for: '$query'")
                                SearchUiState.Empty(query)
                            } else {
                                Log.d("SearchDebug", "[ViewModel] State -> Results (${result.items.size} emails)")
                                SearchUiState.Results(
                                    emails = result.items,
                                    query = query,
                                    nextPageToken = result.nextPageToken
                                )
                            }
                            emit(state)

                            // Save to history only on successful search
                            writeGuard.commit(lease) {
                                saveToHistory(query)
                            }
                        } catch (e: Exception) {
                            Log.e("SearchDebug", "[ViewModel] Exception searching for '$query': ${e.message}", e)
                            emit(
                                SearchUiState.Error(
                                    message = e.message ?: "Something went wrong",
                                    query = query
                                )
                            )
                        }
                    }
                }
                .collect { state ->
                    Log.d("SearchDebug", "[ViewModel] UI State emitted: ${state::class.simpleName}")
                    _uiState.value = state
                }
        }
    }

    // ── Public actions ─────────────────────────────────────────

    fun onQueryChange(newQuery: String) {
        Log.d("SearchDebug", "[ViewModel] onQueryChange: '$newQuery'")
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
                val result = repository.searchEmails(currentQuery, nextPageToken)
                nextPageToken = result.nextPageToken

                val after = _uiState.value
                if (after is SearchUiState.Results) {
                    _uiState.value = after.copy(
                        emails = after.emails + result.items,
                        nextPageToken = nextPageToken,
                        isLoadingNextPage = false
                    )
                }
            } catch (_: Exception) {
                // Silently fail — existing results stay visible
            } finally {
                isLoadingNextPage = false
            }
        }
    }

    fun retry() {
        // Re-trigger the search by re-emitting the current query
        val current = _queryFlow.value
        if (current.length >= 2) {
            _queryFlow.value = current
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
            val lease = writeGuard.capture() ?: return@launch
            writeGuard.commit(lease) {
                historyStore.edit { it.remove(HISTORY_KEY) }
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
                return SearchViewModel(repository, historyStore, writeGuard) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
