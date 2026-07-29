package com.david.mailapp.feature.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
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

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        val testFile = java.io.File(tempFolder.root, "test_search_history.preferences_pb")
        historyStore = PreferenceDataStoreFactory.create { testFile }
        fakeSource = ControllingSearchSource()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Ignore("Contrato pendiente: Fase 3.3")
    @Test
    fun `C7 pagina de consulta A no se anexa a resultados de B`() = runTest(mainDispatcher) {
        val aPage1 = (1..20).map { testEmail(id = "a_$it", subject = "A result $it") }
        val aPage2 = (21..40).map { testEmail(id = "a_$it", subject = "A result $it") }
        val bPage1 = (1..10).map { testEmail(id = "b_$it", subject = "B result $it") }

        val aPage2Gate = CompletableDeferred<Unit>()
        fakeSource.addResults("query-A", null, PaginatedResult(aPage1, "A_page2"))
        fakeSource.addResults("query-A", "A_page2", PaginatedResult(aPage2, null), gate = aPage2Gate)
        fakeSource.addResults("query-B", null, PaginatedResult(bPage1, null))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard())

        viewModel.onQueryChange("query-A")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.runCurrent()

        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        aPage2Gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        assertTrue("A results leaked into B", results.emails.none { it.id.startsWith("a_") })
        assertTrue("B missing expected results", results.emails.all { it.id.startsWith("b_") })
    }

    @Ignore("Contrato pendiente: Fase 3.3")
    @Test
    fun `C7 IDs duplicados en paginacion de B se eliminan`() = runTest(mainDispatcher) {
        val bPage1 = listOf(testEmail(id = "b_1"), testEmail(id = "b_2"))
        val bPage2 = listOf(testEmail(id = "b_1"), testEmail(id = "b_3"))

        fakeSource.addResults("query-B", null, PaginatedResult(bPage1, "B_page2"))
        fakeSource.addResults("query-B", "B_page2", PaginatedResult(bPage2, null))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard())
        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        val ids = results.emails.map { it.id }
        assertEquals("Duplicate IDs present", ids.size, ids.toSet().size)
        assertEquals(3, ids.size)
        assertFalse("isLoadingNextPage should be false", results.isLoadingNextPage)
    }

    @Ignore("Contrato pendiente: Fase 3.3")
    @Test
    fun `C7 cambio de consulta limpia isLoadingNextPage`() = runTest(mainDispatcher) {
        val aPage1 = (1..20).map { testEmail(id = "a_$it") }
        val aPage2 = (21..40).map { testEmail(id = "a_$it") }
        val aPage2Gate = CompletableDeferred<Unit>()

        fakeSource.addResults("query-A", null, PaginatedResult(aPage1, "A_page2"))
        fakeSource.addResults("query-A", "A_page2", PaginatedResult(aPage2, null), gate = aPage2Gate)
        fakeSource.addResults("query-B", null, PaginatedResult(listOf(testEmail(id = "b_1")), null))

        val viewModel = SearchViewModel(fakeSource, historyStore, FakeSessionWriteGuard())

        viewModel.onQueryChange("query-A")
        testScheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testScheduler.advanceUntilIdle()

        viewModel.onQueryChange("query-B")
        testScheduler.advanceUntilIdle()

        aPage2Gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        assertFalse("isLoadingNextPage should be false", results.isLoadingNextPage)
        assertTrue("A results leaked into B", results.emails.all { it.id.startsWith("b_") })
    }
}

private fun testEmail(id: String, subject: String = "Subject $id"): Email = Email(
    id = id, threadId = "thread-$id", from = "sender@test.com",
    fromInitials = "S", to = "me@test.com", subject = subject,
    snippet = "Snippet $id", timestamp = 1000L, isRead = false,
    isStarred = false, hasAttachments = false, labels = emptyList(),
    folder = EmailFolder.Inbox
)
