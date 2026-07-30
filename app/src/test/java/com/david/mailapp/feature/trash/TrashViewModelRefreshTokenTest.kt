package com.david.mailapp.feature.trash

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelRefreshTokenTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(mainDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `partial refresh clears previous token and blocks pagination`() = runTest(mainDispatcher) {
        val source = RecordingTrashSource(completeResult("page-2"))
        val viewModel = TrashViewModel(source)
        advanceUntilIdle()

        source.enqueue(partialResult("must-not-advance"))
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(null, null), source.receivedTokens)
    }

    @Test
    fun `complete refresh replaces previous token`() = runTest(mainDispatcher) {
        val source = RecordingTrashSource(completeResult("page-2"))
        val viewModel = TrashViewModel(source)
        advanceUntilIdle()

        source.enqueue(completeResult("fresh-page"))
        viewModel.refresh()
        advanceUntilIdle()
        source.enqueue(completeResult(null))
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(null, null, "fresh-page"), source.receivedTokens)
    }

    @Test
    fun `partial pagination preserves token for retry`() = runTest(mainDispatcher) {
        val source = RecordingTrashSource(completeResult("page-2"))
        val viewModel = TrashViewModel(source)
        advanceUntilIdle()

        source.enqueue(partialResult("must-not-advance"))
        viewModel.loadNextPage()
        advanceUntilIdle()
        source.enqueue(completeResult(null))
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(null, "page-2", "page-2"), source.receivedTokens)
    }

    private fun completeResult(nextToken: String?) = PaginatedResult(
        items = listOf(email),
        nextPageToken = nextToken,
        isComplete = true
    )

    private fun partialResult(nextToken: String?) = PaginatedResult(
        items = listOf(email),
        nextPageToken = nextToken,
        isComplete = false
    )

    private val email = Email(
        id = "e1", threadId = "t1", from = "from", fromInitials = "F",
        to = "to", subject = "subject", snippet = "snippet", timestamp = 1_000L,
        isRead = false, isStarred = false, hasAttachments = false,
        labels = emptyList(), folder = EmailFolder.Trash
    )
}

private class RecordingTrashSource(initialResult: PaginatedResult<Email>) : TrashEmailSource {
    private val results = ArrayDeque<PaginatedResult<Email>>().apply { add(initialResult) }
    private val room = MutableStateFlow<List<Email>>(emptyList())
    val receivedTokens = mutableListOf<String?>()

    fun enqueue(result: PaginatedResult<Email>) {
        results.add(result)
    }

    override suspend fun refreshTrash(pageToken: String?): PaginatedResult<Email> {
        receivedTokens += pageToken
        return results.removeFirst()
    }

    override fun observeTrash() = room
    override suspend fun deletePermanently(emailId: String) = com.david.mailapp.data.repository.EmailActionResult.Success
    override suspend fun restoreFromTrash(emailId: String) = com.david.mailapp.data.repository.EmailActionResult.Success
}
