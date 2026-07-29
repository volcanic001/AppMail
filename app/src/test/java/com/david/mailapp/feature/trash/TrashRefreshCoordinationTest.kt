package com.david.mailapp.feature.trash

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashRefreshCoordinationTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(mainDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Ignore("Contrato pendiente: Fase 3.2")
    @Test
    fun `C4 Trash refresh tardio no reemplaza datos ni token recientes`() = runTest(mainDispatcher) {
        val source = ControllingTrashSource()
        val oldGate = CompletableDeferred<Unit>()
        val old = source.enqueue(
            PaginatedResult(listOf(email("old")), "old-token"),
            oldGate,
            ignoreCancellation = true
        )
        val viewModel = TrashViewModel(source)
        runCurrent()
        old.started.await()

        source.enqueue(PaginatedResult(listOf(email("new")), "new-token"))
        viewModel.refresh()
        advanceUntilIdle()
        oldGate.complete(Unit)
        advanceUntilIdle()

        source.enqueue(PaginatedResult(emptyList(), null))
        viewModel.loadNextPage()
        advanceUntilIdle()

        val ids = (viewModel.uiState.value as TrashUiState.Success).emails.map { it.id }
        val usedToken = source.receivedTokens.last()
        assertTrue(
            "Latest Trash refresh must own state and token; ids=$ids token=$usedToken",
            ids == listOf("new") && usedToken == "new-token"
        )
    }

    @Ignore("Contrato pendiente: Fase 3.2")
    @Test
    fun `C5 Trash refresh nuevo cancela paginacion y refresh anterior`() = runTest(mainDispatcher) {
        val source = ControllingTrashSource()
        source.enqueue(PaginatedResult(listOf(email("initial")), "page-2"))
        val viewModel = TrashViewModel(source)
        advanceUntilIdle()

        val pageGate = CompletableDeferred<Unit>()
        val page = source.enqueue(PaginatedResult(emptyList(), null), pageGate)
        viewModel.loadNextPage()
        runCurrent()
        page.started.await()

        val refreshGate = CompletableDeferred<Unit>()
        val refreshA = source.enqueue(PaginatedResult(listOf(email("refresh-a")), "a-token"), refreshGate)
        viewModel.refresh()
        runCurrent()
        refreshA.started.await()

        source.enqueue(PaginatedResult(listOf(email("refresh-b")), "b-token"))
        try {
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(
                "New Trash refresh must cancel pagination and the prior refresh",
                page.cancelled.isCompleted && refreshA.cancelled.isCompleted
            )
        } finally {
            pageGate.complete(Unit)
            refreshGate.complete(Unit)
            advanceUntilIdle()
        }
    }
}

private class ControllingTrashSource : TrashEmailSource {
    data class Plan(
        val result: PaginatedResult<Email>,
        val gate: CompletableDeferred<Unit>?,
        val ignoreCancellation: Boolean,
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val cancelled: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private val room = MutableStateFlow<List<Email>>(emptyList())
    private val plans = mutableListOf<Plan>()
    val receivedTokens = mutableListOf<String?>()

    fun enqueue(
        result: PaginatedResult<Email>,
        gate: CompletableDeferred<Unit>? = null,
        ignoreCancellation: Boolean = false
    ) = Plan(result, gate, ignoreCancellation).also(plans::add)

    override fun observeTrash() = room

    override suspend fun refreshTrash(pageToken: String?): PaginatedResult<Email> {
        receivedTokens += pageToken
        val plan = plans.removeAt(0)
        plan.started.complete(Unit)
        try {
            plan.gate?.await()
        } catch (cancelled: CancellationException) {
            plan.cancelled.complete(Unit)
            if (!plan.ignoreCancellation) throw cancelled
            withContext(NonCancellable) { plan.gate?.await() }
        }
        room.value = if (pageToken == null) plan.result.items else room.value + plan.result.items
        return plan.result
    }

    override suspend fun deletePermanently(emailId: String) = Unit
    override suspend fun restoreFromTrash(emailId: String) = Unit
}

private fun email(id: String) = Email(
    id = id, threadId = "thread-$id", from = "sender@test.com", fromInitials = "S",
    to = "me@test.com", subject = id, snippet = id, timestamp = 1_000L,
    isRead = false, isStarred = false, hasAttachments = false,
    labels = emptyList(), folder = EmailFolder.Trash
)
