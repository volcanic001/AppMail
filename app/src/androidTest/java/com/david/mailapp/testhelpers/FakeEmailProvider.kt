package com.david.mailapp.testhelpers

import com.david.mailapp.data.remote.provider.EmailLookupResult
import com.david.mailapp.data.remote.provider.EmailProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
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
    var downloadAttachmentStarted: CompletableDeferred<Unit>? = null
    var fetchEmailByIdStarted: CompletableDeferred<Unit>? = null
    var downloadInlineImagesDeferred: CompletableDeferred<Unit>? = null

    var wasCancelledFetchEmailById = false
    var completedFetchEmailById = false
    var ignoreCancellationFetchEmailById = false
    var wasCancelledInlineImages = false
    var ignoreCancellationInlineImages = false
    var wasCancelledDownloadAttachment = false
    var ignoreCancellationDownloadAttachment = false

    var fetchInboxResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var fetchTrashResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var searchResult: PaginatedResult<Email> = PaginatedResult(emptyList(), null)
    var fetchEmailByIdResult: EmailLookupResult = EmailLookupResult.NotFound
    var inlineImagesResult: Map<String, String> = emptyMap()
    var userEmailResult: String? = "test@example.com"
    var getUserEmailCalls = 0
    var getUserEmailError: Exception? = null
    var downloadAttachmentResult: ByteArray = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

    var moveToTrashCalls = 0
    var restoreFromTrashCalls = 0
    var deletePermanentlyCalls = 0
    var markAsReadCalls = 0
    var sendEmailCalls = 0
    var downloadAttachmentCalls = 0
    var fetchInboxCalls = 0
    var fetchTrashCalls = 0
    var searchCalls = 0
    var fetchEmailByIdCalls = 0
    var inlineImagesCalls = 0
    var eventLog: MutableList<String>? = null
    val receivedInboxTokens = mutableListOf<String?>()
    val receivedTrashTokens = mutableListOf<String?>()
    val receivedSearchRequests = mutableListOf<Pair<String, String?>>()
    val receivedFetchEmailByIdIds = mutableListOf<String>()
    val receivedInlineImageRequests = mutableListOf<Pair<String, List<com.david.mailapp.domain.model.EmailInlineReference>>>()
    val receivedDownloadAttachmentRequests = mutableListOf<Pair<String, String>>()
    val receivedSendRequests = mutableListOf<SendRequest>()

    var moveToTrashError: Exception? = null
    var restoreFromTrashError: Exception? = null
    var deletePermanentlyError: Exception? = null
    var markAsReadError: Exception? = null
    var sendEmailError: Exception? = null
    var downloadAttachmentError: Exception? = null
    var fetchInboxError: Exception? = null
    var fetchTrashError: Exception? = null
    var searchError: Exception? = null
    var fetchEmailByIdError: Exception? = null
    var fetchEmailByIdDeferred: CompletableDeferred<Unit>? = null

    /** Per-call gates: call N blocks on deferredByCall[N] before returning. Ignored when empty. */
    var fetchEmailByIdDeferredByCall: List<CompletableDeferred<Unit>> = emptyList()

    /** Per-call results: call N returns resultsByCall[N]. Falls back to [fetchEmailByIdResult] when empty. */
    var fetchEmailByIdResultsByCall: List<EmailLookupResult> = emptyList()
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

    override suspend fun search(query: String, pageToken: String?): PaginatedResult<Email> {
        searchCalls++
        receivedSearchRequests += query to pageToken
        searchDeferred?.await()
        searchError?.let { throw it }
        return searchResult
    }

    override suspend fun fetchEmailById(emailId: String): EmailLookupResult {
        eventLog?.add("gmail.fetchEmailById")
        fetchEmailByIdCalls++
        receivedFetchEmailByIdIds += emailId
        fetchEmailByIdStarted?.complete(Unit)
        val callIndex = fetchEmailByIdCalls - 1
        val perCallGate = fetchEmailByIdDeferredByCall.getOrNull(callIndex)
        if (perCallGate != null) {
            perCallGate.await()
        } else {
            val deferred = fetchEmailByIdDeferred
            if (deferred != null) {
                try {
                    deferred.await()
                } catch (e: CancellationException) {
                    wasCancelledFetchEmailById = true
                    if (!ignoreCancellationFetchEmailById) throw e
                    withContext(NonCancellable) { deferred.await() }
                }
            }
        }
        fetchEmailByIdError?.let { throw it }
        completedFetchEmailById = true
        return fetchEmailByIdResultsByCall.getOrNull(callIndex) ?: fetchEmailByIdResult
    }

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

    override suspend fun downloadInlineImages(emailId: String, refs: List<com.david.mailapp.domain.model.EmailInlineReference>): Map<String, String> {
        inlineImagesCalls++
        receivedInlineImageRequests += emailId to refs
        val deferred = downloadInlineImagesDeferred
        if (deferred != null) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                wasCancelledInlineImages = true
                if (!ignoreCancellationInlineImages) throw e
                withContext(NonCancellable) { deferred.await() }
            }
        }
        inlineImagesError?.let { throw it }
        return inlineImagesResult
    }

    override suspend fun getUserEmail(): String? {
        eventLog?.add("gmail.getUserEmail")
        getUserEmailCalls++
        getUserEmailError?.let { throw it }
        return userEmailResult
    }

    override suspend fun downloadAttachment(emailId: String, attachmentId: String): ByteArray {
        eventLog?.add("gmail.downloadAttachment")
        downloadAttachmentCalls++
        receivedDownloadAttachmentRequests += emailId to attachmentId
        downloadAttachmentStarted?.complete(Unit)
        val deferred = downloadAttachmentDeferred
        if (deferred != null) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                wasCancelledDownloadAttachment = true
                if (!ignoreCancellationDownloadAttachment) throw e
                withContext(NonCancellable) { deferred.await() }
            }
        }
        downloadAttachmentError?.let { throw it }
        return downloadAttachmentResult
    }

    override suspend fun sendEmail(
        to: String, cc: String?, bcc: String?, subject: String, body: String,
        replyContext: ReplyContext?
    ) {
        eventLog?.add("gmail.sendEmail")
        sendEmailCalls++
        receivedSendRequests += SendRequest(to, cc, bcc, subject, body, replyContext)
        sendEmailDeferred?.await()
        sendEmailError?.let { throw it }
    }
}

data class SendRequest(
    val to: String,
    val cc: String?,
    val bcc: String?,
    val subject: String,
    val body: String,
    val replyContext: ReplyContext?
)

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

fun testFetchedEmail(
    id: String,
    folder: EmailFolder = EmailFolder.Inbox,
    from: String = "sender@test.com",
    subject: String = "Subject $id",
    snippet: String = "Snippet $id",
    isRead: Boolean = false,
    timestamp: Long = 1000L
) = testEmail(id, folder, from, subject, snippet, isRead, timestamp).copy(
    pdfMetadataScanned = true,
    contentState = EmailContentState.EMPTY,
    bodyKind = EmailBodyKind.UNKNOWN
)

fun testHeavyEmail(id: String, sizeBytes: Int = 1_000_000): Email {
    val largeBody = "A".repeat(sizeBytes)
    return testEmail(id).copy(
        body = "<html><body>$largeBody</body></html>",
        cleanBody = largeBody,
        pdfAttachments = listOf(
            com.david.mailapp.domain.model.PdfAttachmentMetadata(
                fileName = "huge_$id.pdf",
                mimeType = "application/pdf",
                attachmentId = "att_$id",
                sizeBytes = 2_000_000L,
                partId = "1.1"
            )
        ),
        pdfMetadataScanned = true,
        contentState = EmailContentState.READY,
        bodyKind = EmailBodyKind.HTML,
        cachedContentBytes = (
            "<html><body>$largeBody</body></html>".toByteArray(Charsets.UTF_8).size +
                largeBody.toByteArray(Charsets.UTF_8).size +
                2
            ).toLong(),
        rfcMessageId = "<$id@heavy.com>",
        rfcReferences = "<parent@heavy.com>"
    )
}
