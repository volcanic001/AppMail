package com.david.mailapp.testhelpers

import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class FakeEmailProvider : EmailProvider {

    data class FolderFetchPlan(
        val result: PaginatedResult<Email>,
        val gate: CompletableDeferred<Unit>? = null,
        val ignoreCancellation: Boolean = false,
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val cancelled: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private val inboxPlans = mutableListOf<FolderFetchPlan>()
    private val trashPlans = mutableListOf<FolderFetchPlan>()

    fun enqueueInbox(
        result: PaginatedResult<Email>,
        gate: CompletableDeferred<Unit>? = null,
        ignoreCancellation: Boolean = false
    ) = FolderFetchPlan(result, gate, ignoreCancellation).also(inboxPlans::add)

    fun enqueueTrash(
        result: PaginatedResult<Email>,
        gate: CompletableDeferred<Unit>? = null,
        ignoreCancellation: Boolean = false
    ) = FolderFetchPlan(result, gate, ignoreCancellation).also(trashPlans::add)

    var fetchInboxDeferred: CompletableDeferred<Unit>? = null
    var fetchTrashDeferred: CompletableDeferred<Unit>? = null
    var searchDeferred: CompletableDeferred<Unit>? = null
    var moveToTrashDeferred: CompletableDeferred<Unit>? = null
    var restoreFromTrashDeferred: CompletableDeferred<Unit>? = null
    var deletePermanentlyDeferred: CompletableDeferred<Unit>? = null
    var markAsReadDeferred: CompletableDeferred<Unit>? = null
    var sendEmailDeferred: CompletableDeferred<Unit>? = null
    var downloadAttachmentDeferred: CompletableDeferred<Unit>? = null

    var fetchInboxResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var fetchTrashResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var searchResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var fetchBodyResult: BodyFetchResult? = null
    var inlineImagesResult: Map<String, String> = emptyMap()
    var userEmailResult: String? = "test@example.com"
    var downloadAttachmentResult: ByteArray = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

    var moveToTrashCalls = 0
    var restoreFromTrashCalls = 0
    var deletePermanentlyCalls = 0
    var markAsReadCalls = 0
    var sendEmailCalls = 0
    var downloadAttachmentCalls = 0
    var fetchInboxCalls = 0
    var fetchTrashCalls = 0
    var fetchBodyCalls = 0
    var inlineImagesCalls = 0
    var eventLog: MutableList<String>? = null
    val receivedInboxTokens = mutableListOf<String?>()
    val receivedTrashTokens = mutableListOf<String?>()

    var moveToTrashError: Exception? = null
    var restoreFromTrashError: Exception? = null
    var deletePermanentlyError: Exception? = null
    var markAsReadError: Exception? = null
    var sendEmailError: Exception? = null
    var downloadAttachmentError: Exception? = null
    var fetchInboxError: Exception? = null
    var fetchTrashError: Exception? = null
    var searchError: Exception? = null
    var fetchBodyError: Exception? = null
    var inlineImagesError: Exception? = null

    override suspend fun fetchInbox(pageToken: String?): PaginatedResult<Email> {
        eventLog?.add("gmail.fetch.inbox")
        fetchInboxCalls++
        receivedInboxTokens += pageToken
        if (inboxPlans.isNotEmpty()) return execute(inboxPlans.removeAt(0))
        fetchInboxDeferred?.await()
        fetchInboxError?.let { throw it }
        return fetchInboxResult
    }

    override suspend fun fetchTrash(pageToken: String?): PaginatedResult<Email> {
        eventLog?.add("gmail.fetch.trash")
        fetchTrashCalls++
        receivedTrashTokens += pageToken
        if (trashPlans.isNotEmpty()) return execute(trashPlans.removeAt(0))
        fetchTrashDeferred?.await()
        fetchTrashError?.let { throw it }
        return fetchTrashResult
    }

    private suspend fun execute(plan: FolderFetchPlan): PaginatedResult<Email> {
        plan.started.complete(Unit)
        val gate = plan.gate
        if (gate != null) {
            try {
                gate.await()
            } catch (cancelled: CancellationException) {
                plan.cancelled.complete(Unit)
                if (!plan.ignoreCancellation) throw cancelled
                withContext(NonCancellable) { gate.await() }
            }
        }
        return plan.result
    }

    override suspend fun search(query: String, pageToken: String?) = searchResult

    override suspend fun moveToTrash(emailId: String) {
        eventLog?.add("gmail.moveToTrash")
        moveToTrashDeferred?.await()
        moveToTrashCalls++
        moveToTrashError?.let { throw it }
    }

    override suspend fun restoreFromTrash(emailId: String) {
        eventLog?.add("gmail.restoreFromTrash")
        restoreFromTrashDeferred?.await()
        restoreFromTrashCalls++
        restoreFromTrashError?.let { throw it }
    }

    override suspend fun deletePermanently(emailId: String) {
        eventLog?.add("gmail.deletePermanently")
        deletePermanentlyDeferred?.await()
        deletePermanentlyCalls++
        deletePermanentlyError?.let { throw it }
    }

    override suspend fun markAsRead(emailId: String) {
        eventLog?.add("gmail.markAsRead")
        markAsReadDeferred?.await()
        markAsReadCalls++
        markAsReadError?.let { throw it }
    }

    override suspend fun fetchBodyWithRefs(emailId: String): BodyFetchResult? {
        fetchBodyCalls++
        fetchBodyError?.let { throw it }
        return fetchBodyResult
    }

    override suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String> {
        inlineImagesCalls++
        inlineImagesError?.let { throw it }
        return inlineImagesResult
    }

    override suspend fun getUserEmail() = userEmailResult

    override suspend fun downloadAttachment(emailId: String, attachmentId: String): ByteArray {
        downloadAttachmentDeferred?.await()
        downloadAttachmentCalls++
        downloadAttachmentError?.let { throw it }
        return downloadAttachmentResult
    }

    override suspend fun sendEmail(
        to: String, cc: String?, bcc: String?, subject: String, body: String,
        replyContext: ReplyContext?
    ) {
        sendEmailDeferred?.await()
        sendEmailCalls++
        sendEmailError?.let { throw it }
    }
}

fun testEmail(
    id: String,
    folder: EmailFolder = EmailFolder.Inbox,
    from: String = "sender@test.com",
    subject: String = "Subject $id",
    snippet: String = "Snippet $id",
    isRead: Boolean = false,
    timestamp: Long = 1000L
) = Email(
    id = id, threadId = "thread-$id", from = from, fromInitials = "S",
    to = "me@test.com", subject = subject, snippet = snippet,
    timestamp = timestamp, isRead = isRead, isStarred = false,
    hasAttachments = false, labels = emptyList(), folder = folder
)
