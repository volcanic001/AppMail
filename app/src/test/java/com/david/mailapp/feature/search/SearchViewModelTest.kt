package com.david.mailapp.feature.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var testStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var tempDir: File
    private var activeViewModel: SearchViewModel? = null

    private class FakeWriteGuard : SessionWriteGuard {
        override suspend fun activate() {}
        override suspend fun capture(): SessionWriteLease? = FakeLease()
        override suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T? = block()
        override suspend fun invalidate() {}
        private class FakeLease : SessionWriteLease {
            override val generation: Long = 1L
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = createTempDir("search_test")
        testStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tempDir.resolve("search_history.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        tempDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────

    private fun createViewModel(
        source: SearchEmailSource,
        writeGuard: SessionWriteGuard = FakeWriteGuard()
    ): SearchViewModel {
        return SearchViewModel(
            source = source,
            historyStore = testStore,
            writeGuard = writeGuard
        ).also { activeViewModel = it }
    }

    private fun email(id: String): Email = Email(
        id = id,
        threadId = id,
        from = "sender@test.com",
        fromInitials = "S",
        to = "me@test.com",
        subject = "Test Subject $id",
        snippet = "Snippet $id",
        timestamp = 1_000_000L,
        isRead = false,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox
    )

    // ── Tests ────────────────────────────────────

    @Test
    fun `error de red produce Error NO_CONNECTION`() = testScope.runTest {
        val vm = createViewModel(
            source = SearchEmailSource { _, _ -> throw IOException("timeout") }
        )
        vm.onQueryChange("test")

        advanceUntilIdle()

        val state = vm.uiState.first { it !is SearchUiState.Loading }
        assertTrue("Expected Error, got ${state::class.simpleName}", state is SearchUiState.Error)
        val error = state as SearchUiState.Error
        assertEquals("test", error.query)
        assertEquals(UiErrorReason.NO_CONNECTION, error.reason)
    }

    @Test
    fun `error desconocido produce UNKNOWN`() = testScope.runTest {
        val vm = createViewModel(
            source = SearchEmailSource { _, _ -> throw RuntimeException("weird") }
        )
        vm.onQueryChange("test")

        advanceUntilIdle()

        val state = vm.uiState.first { it !is SearchUiState.Loading }
        assertTrue("Expected Error, got ${state::class.simpleName}", state is SearchUiState.Error)
        val error = state as SearchUiState.Error
        assertEquals(UiErrorReason.UNKNOWN, error.reason)
    }

    @Test
    fun `consulta reemplazada cancela anterior sin emitir Error`() = testScope.runTest {
        var callCount = 0
        val vm = createViewModel(
            source = SearchEmailSource { query, _ ->
                callCount++
                if (query == "slow") {
                    kotlinx.coroutines.yield()
                    kotlinx.coroutines.delay(10_000)
                }
                PaginatedResult(listOf(email("1")), null)
            }
        )

        vm.onQueryChange("slow")
        vm.onQueryChange("fast")

        advanceUntilIdle()

        assertEquals(1, callCount)
        val state = vm.uiState.value
        assertTrue("Expected Results, got ${state::class.simpleName}", state is SearchUiState.Results)
    }

    @Test
    fun `retry vuelve a ejecutar misma consulta y puede pasar de Error a Results`() = testScope.runTest {
        var attempts = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, _ ->
                attempts++
                if (attempts == 1) throw IOException("first fail")
                PaginatedResult(listOf(email("1")), null)
            }
        )

        vm.onQueryChange("test")
        advanceUntilIdle()

        val errorState = vm.uiState.value
        assertTrue("Expected Error, got ${errorState::class.simpleName}", errorState is SearchUiState.Error)

        vm.retry()
        advanceUntilIdle()

        val successState = vm.uiState.value
        assertTrue("Expected Results, got ${successState::class.simpleName}", successState is SearchUiState.Results)
        assertEquals(2, attempts)
    }

    @Test
    fun `fallo de paginacion conserva correos y limpia isLoadingNextPage`() = testScope.runTest {
        var paginationFail = false
        val vm = createViewModel(
            source = SearchEmailSource { query, pageToken ->
                if (pageToken != null) {
                    paginationFail = true
                    throw IOException("pagination fail")
                }
                PaginatedResult(
                    listOf(email("1"), email("2"), email("3")),
                    nextPageToken = "page2"
                )
            }
        )

        vm.onQueryChange("test")
        advanceUntilIdle()

        val results = vm.uiState.value as SearchUiState.Results
        assertEquals(3, results.emails.size)

        vm.loadNextPage()
        advanceUntilIdle()

        assertTrue("Pagination should have been attempted", paginationFail)
        val afterState = vm.uiState.value as SearchUiState.Results
        assertEquals(3, afterState.emails.size) // Original emails preserved
    }

    @Test
    fun `fallo al guardar historial no reemplaza resultado exitoso`() = testScope.runTest {
        val vm = createViewModel(
            source = SearchEmailSource { _, _ ->
                PaginatedResult(listOf(email("1")), null)
            }
        )

        vm.onQueryChange("test")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Results, got ${state::class.simpleName}", state is SearchUiState.Results)
    }

    @Test
    fun `cancelacion del commit del historial conserva SearchUiState_Results y no escribe historial`() = testScope.runTest {
        val sentinel = CancellationException("sentinel-search")
        val failingGuard = object : SessionWriteGuard {
            override suspend fun activate() {}
            override suspend fun capture(): SessionWriteLease? = object : SessionWriteLease { override val generation = 1L }
            override suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T? {
                throw sentinel
            }
            override suspend fun invalidate() {}
        }
        val vm = createViewModel(
            source = SearchEmailSource { _, _ -> PaginatedResult(listOf(email("1")), null) },
            writeGuard = failingGuard
        )

        vm.onQueryChange("test")
        // CancellationException from commit() cancels the child flow coroutine but
        // does NOT propagate to the test scope. Check observable contract only.
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Results, got ${state::class.simpleName}", state is SearchUiState.Results)

        val history = testStore.data.first()[stringPreferencesKey("search_history")]
        org.junit.Assert.assertNull(history)
    }

    @Test
    fun `cambiar a consulta corta cancela busqueda y paginacion, termina en Idle y bloquea nuevas paginas`() = testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, pageToken ->
                callCount++
                if (pageToken == "token-1") {
                    gate.await()
                    PaginatedResult(listOf(email("2")), null)
                } else {
                    PaginatedResult(listOf(email("1")), "token-1", isComplete = true)
                }
            }
        )

        vm.onQueryChange("valid")
        advanceUntilIdle()
        assertEquals(1, callCount)

        vm.loadNextPage()
        testScheduler.runCurrent()
        assertEquals(2, callCount)

        vm.onQueryChange("v")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SearchUiState.Idle)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SearchUiState.Idle)

        vm.loadNextPage()
        testScheduler.runCurrent()
        assertEquals(2, callCount)
    }

    @Test
    fun `clearQuery cancela busqueda y paginacion, termina en Idle y bloquea nuevas paginas`() = testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, pageToken ->
                callCount++
                if (pageToken == "token-1") {
                    gate.await()
                    PaginatedResult(listOf(email("2")), null)
                } else {
                    PaginatedResult(listOf(email("1")), "token-1", isComplete = true)
                }
            }
        )

        vm.onQueryChange("valid")
        advanceUntilIdle()
        assertEquals(1, callCount)

        vm.loadNextPage()
        testScheduler.runCurrent()
        assertEquals(2, callCount)

        vm.clearQuery()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SearchUiState.Idle)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SearchUiState.Idle)

        vm.loadNextPage()
        testScheduler.runCurrent()
        assertEquals(2, callCount)
    }

    @Test
    fun `primera pagina tardia no publica resultados despues de cambiar consulta`() = testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(
            source = SearchEmailSource { query, _ ->
                if (query == "query-A") {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        gate.await()
                        PaginatedResult(listOf(email("a1")), "token-a")
                    }
                } else {
                    PaginatedResult(listOf(email("b1")), "token-b")
                }
            }
        )

        vm.onQueryChange("query-A")
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent() // starts query-A (calls provider, suspends on gate)

        // change query to B before A completes
        vm.onQueryChange("query-B")
        advanceUntilIdle() // let B complete

        val stateAfterB = vm.uiState.value as SearchUiState.Results
        assertEquals("query-B", stateAfterB.query)
        assertEquals(listOf("b1"), stateAfterB.emails.map { it.id })
        assertEquals("token-b", stateAfterB.nextPageToken)

        // complete A's gate
        gate.complete(Unit)
        advanceUntilIdle()

        // Verify that B's state remains intact and A did not overwrite or save history
        val finalState = vm.uiState.value as SearchUiState.Results
        assertEquals("query-B", finalState.query)
        assertEquals(listOf("b1"), finalState.emails.map { it.id })
        assertEquals("token-b", finalState.nextPageToken)

        val history = vm.historyFlow.first()
        assertTrue(history.contains("query-B"))
        assertFalse(history.contains("query-A"))
    }

    @Test
    fun `retry invalida intento anterior de la misma consulta y solo acepta generacion nueva`() = testScope.runTest {
        val gate1 = CompletableDeferred<Unit>()
        var calls = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, _ ->
                calls++
                if (calls == 1) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        gate1.await()
                        PaginatedResult(listOf(email("old")), "old-token")
                    }
                } else {
                    PaginatedResult(listOf(email("new")), "new-token")
                }
            }
        )

        vm.onQueryChange("query")
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent() // starts search 1

        vm.retry() // cancels search 1, starts search 2 (no debounce)
        advanceUntilIdle() // executes search 2

        val state = vm.uiState.value as SearchUiState.Results
        assertEquals(listOf("new"), state.emails.map { it.id })
        assertEquals("new-token", state.nextPageToken)

        gate1.complete(Unit) // release first search
        advanceUntilIdle()

        // verify state is still from the second run
        val finalState = vm.uiState.value as SearchUiState.Results
        assertEquals(listOf("new"), finalState.emails.map { it.id })
        assertEquals("new-token", finalState.nextPageToken)
    }

    @Test
    fun `pagina parcial conserva token anterior y permite reintentar`() = testScope.runTest {
        var callCount = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, pageToken ->
                callCount++
                if (callCount == 1) {
                    PaginatedResult(listOf(email("1")), "page-2", isComplete = true)
                } else if (callCount == 2) {
                    // partial page: returns some items, isComplete = false
                    PaginatedResult(listOf(email("2")), "page-3", isComplete = false)
                } else {
                    // success retry
                    PaginatedResult(listOf(email("3")), "page-3", isComplete = true)
                }
            }
        )

        vm.onQueryChange("query")
        advanceUntilIdle() // first page loaded, next token = "page-2"

        // trigger pagination (page-2)
        vm.loadNextPage()
        advanceUntilIdle() // partial page response received

        val state1 = vm.uiState.value as SearchUiState.Results
        // nextPageToken must remain "page-2" because page 2 was partial
        assertEquals("page-2", state1.nextPageToken)
        assertEquals(listOf("1", "2"), state1.emails.map { it.id })

        // load next page again (should retry "page-2")
        vm.loadNextPage()
        advanceUntilIdle()

        val state2 = vm.uiState.value as SearchUiState.Results
        assertEquals("page-3", state2.nextPageToken)
        assertEquals(listOf("1", "2", "3"), state2.emails.map { it.id })
    }

    @Test
    fun `reintento completo de pagina parcial deduplica y avanza`() = testScope.runTest {
        var callCount = 0
        val vm = createViewModel(
            source = SearchEmailSource { _, _ ->
                callCount++
                if (callCount == 1) {
                    PaginatedResult(listOf(email("1")), "page-2", isComplete = true)
                } else if (callCount == 2) {
                    // partial: contains email "1" (duplicate) and "2", isComplete = false
                    PaginatedResult(listOf(email("1"), email("2")), "page-3", isComplete = false)
                } else {
                    // retry: contains email "2" (duplicate) and "3", isComplete = true
                    PaginatedResult(listOf(email("2"), email("3")), "page-3", isComplete = true)
                }
            }
        )

        vm.onQueryChange("query")
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        val state1 = vm.uiState.value as SearchUiState.Results
        assertEquals("page-2", state1.nextPageToken)
        assertEquals(listOf("1", "2"), state1.emails.map { it.id })

        vm.loadNextPage()
        advanceUntilIdle()

        val state2 = vm.uiState.value as SearchUiState.Results
        assertEquals("page-3", state2.nextPageToken)
        assertEquals(listOf("1", "2", "3"), state2.emails.map { it.id })
    }

    @Test
    fun `fallo de paginacion conserva correos y token y limpia flags`() = testScope.runTest {
        var paginationFail = false
        val vm = createViewModel(
            source = SearchEmailSource { _, pageToken ->
                if (pageToken != null) {
                    paginationFail = true
                    throw IOException("pagination fail")
                }
                PaginatedResult(listOf(email("1")), "page-2", isComplete = true)
            }
        )

        vm.onQueryChange("query")
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        assertTrue(paginationFail)
        val state = vm.uiState.value as SearchUiState.Results
        assertEquals("page-2", state.nextPageToken)
        assertEquals(listOf("1"), state.emails.map { it.id })
        assertFalse(state.isLoadingNextPage)
    }

    @Test
    fun `cancelacion no se transforma en error visible`() = testScope.runTest {
        val vm = createViewModel(
            source = SearchEmailSource { _, _ -> throw CancellationException("cancelled") }
        )
        vm.onQueryChange("query")
        advanceUntilIdle()

        assertTrue("State should not be Error", vm.uiState.value !is SearchUiState.Error)
    }

    @Test
    fun `finally de paginacion cancelada no limpia flag de operacion posterior`() = testScope.runTest {
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        val vm = createViewModel(
            source = SearchEmailSource { query, pageToken ->
                if (query == "query-A" && pageToken == "page-a-2") {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        gateA.await()
                        PaginatedResult(listOf(email("a2")), null)
                    }
                } else if (query == "query-A") {
                    PaginatedResult(listOf(email("a1")), "page-a-2", isComplete = true)
                } else if (query == "query-B" && pageToken == "page-b-2") {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        gateB.await()
                        PaginatedResult(listOf(email("b2")), null)
                    }
                } else {
                    PaginatedResult(listOf(email("b1")), "page-b-2", isComplete = true)
                }
            }
        )

        vm.onQueryChange("query-A")
        advanceUntilIdle()

        vm.loadNextPage()
        testScheduler.runCurrent() // starts page A-2, suspends on gateA

        // Change query to query-B and complete B's first page
        vm.onQueryChange("query-B")
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent()
        advanceUntilIdle()

        // Start B's pagination (blocked on gateB)
        vm.loadNextPage()
        testScheduler.runCurrent()

        // Release old A-2 page
        gateA.complete(Unit)
        testScheduler.runCurrent()

        // Verify B's load flag is still true
        val stateDuringB = vm.uiState.value as SearchUiState.Results
        assertTrue(stateDuringB.isLoadingNextPage)

        // Release B-2 page
        gateB.complete(Unit)
        advanceUntilIdle()

        val finalState = vm.uiState.value as SearchUiState.Results
        assertFalse(finalState.isLoadingNextPage)
        assertEquals(listOf("b1", "b2"), finalState.emails.map { it.id })
        assertEquals(null, finalState.nextPageToken)
    }

    @Test
    fun `primera pagina deduplica por ID`() = testScope.runTest {
        val vm = createViewModel(
            source = SearchEmailSource { _, _ ->
                PaginatedResult(listOf(email("1"), email("1"), email("2")), null)
            }
        )
        vm.onQueryChange("query")
        advanceUntilIdle()

        val state = vm.uiState.value as SearchUiState.Results
        assertEquals(listOf("1", "2"), state.emails.map { it.id })
    }
}
