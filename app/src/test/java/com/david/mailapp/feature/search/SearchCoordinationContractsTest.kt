package com.david.mailapp.feature.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * C7 — Search coordination contracts (JVM).
 * Activation: Fase 3.3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchCoordinationContractsTest {

    @Rule @JvmField val tempFolder = TemporaryFolder()

    private lateinit var historyStore: DataStore<Preferences>
    private lateinit var fakeSource: ControllingSearchSource
    private val mainDispatcher = StandardTestDispatcher()
    private var activeViewModel: SearchViewModel? = null

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        val testFile = java.io.File(tempFolder.root, "test_search_history.preferences_pb")
        historyStore = PreferenceDataStoreFactory.create { testFile }
        fakeSource = ControllingSearchSource()
    }

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.cancel()
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `C7 pagina de consulta A no se anexa a resultados de B`() = runTest(mainDispatcher) {
        val aPage1 = (1..20).map { testEmail(id = "a_$it", subject = "A result $it") }
        val aPage2 = (21..40).map { testEmail(id = "a_$it", subject = "A result $it") }
        val bPage1 = (1..10).map { testEmail(id = "b_$it", subject = "B result $it") }

        val aPage2Gate = CompletableDeferred<Unit>()
        fakeSource.addResults("query-A", null, PaginatedResult(aPage1, "A_page2"))
        val aPage2Entry = fakeSource.addResults("query-A", "A_page2", PaginatedResult(aPage2, null), gate = aPage2Gate, ignoreCancellation = true)
        fakeSource.addResults("query-B", null, PaginatedResult(bPage1, "B_page2"))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard(), SavedStateHandle()).also { activeViewModel = it }

        viewModel.onQueryChange("query-A")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.runCurrent()
        aPage2Entry.started.await()

        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        aPage2Gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        assertTrue("A results leaked into B", results.emails.none { it.id.startsWith("a_") })
        assertTrue("B missing expected results", results.emails.all { it.id.startsWith("b_") })
        assertEquals("query-B", results.query)
        assertEquals("B_page2", results.nextPageToken)
        assertFalse("isLoadingNextPage should be false", results.isLoadingNextPage)
        assertTrue("A page 2 should have registered cancellation", aPage2Entry.cancelled.isCompleted)
    }

    @Test
    fun `C7 IDs duplicados en paginacion de B se eliminan`() = runTest(mainDispatcher) {
        val bPage1 = listOf(testEmail(id = "b_1"), testEmail(id = "b_2"))
        val bPage2 = listOf(testEmail(id = "b_1"), testEmail(id = "b_3"), testEmail(id = "b_3"))

        fakeSource.addResults("query-B", null, PaginatedResult(bPage1, "B_page2"))
        fakeSource.addResults("query-B", "B_page2", PaginatedResult(bPage2, null))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard(), SavedStateHandle()).also { activeViewModel = it }
        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        val ids = results.emails.map { it.id }
        assertEquals("Duplicate IDs present", ids.toSet().size, ids.size)
        assertEquals(listOf("b_1", "b_2", "b_3"), ids)
        assertFalse("isLoadingNextPage should be false", results.isLoadingNextPage)
    }

    @Test
    fun `C7 cambio de consulta limpia isLoadingNextPage`() = runTest(mainDispatcher) {
        val aPage1 = (1..20).map { testEmail(id = "a_$it") }
        val aPage2 = (21..40).map { testEmail(id = "a_$it") }
        val aPage2Gate = CompletableDeferred<Unit>()

        fakeSource.addResults("query-A", null, PaginatedResult(aPage1, "A_page2"))
        val aPage2Entry = fakeSource.addResults("query-A", "A_page2", PaginatedResult(aPage2, null), gate = aPage2Gate, ignoreCancellation = false)
        fakeSource.addResults("query-B", null, PaginatedResult(listOf(testEmail(id = "b_1")), null))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard(), SavedStateHandle()).also { activeViewModel = it }

        viewModel.onQueryChange("query-A")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.runCurrent()
        aPage2Entry.started.await()

        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        aPage2Gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        assertFalse("isLoadingNextPage should be false", results.isLoadingNextPage)
        assertTrue("A results leaked into B", results.emails.all { it.id.startsWith("b_") })
        assertTrue("A page 2 should have registered cancellation", aPage2Entry.cancelled.isCompleted)
    }
}

private fun testEmail(id: String, subject: String = "Subject $id"): Email = Email(
    id = id, threadId = "thread-$id", from = "sender@test.com",
    fromInitials = "S", to = "me@test.com", subject = subject,
    snippet = "Snippet $id", timestamp = 1000L, isRead = false,
    isStarred = false, hasAttachments = false, labels = emptyList(),
    folder = EmailFolder.Inbox
)
