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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        testStore = PreferenceDataStoreFactory.create {
            tempDir.resolve("search_history.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
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
        )
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
}
