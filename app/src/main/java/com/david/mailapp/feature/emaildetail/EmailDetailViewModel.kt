package com.david.mailapp.feature.emaildetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.repository.EmailResolutionFailureReason
import com.david.mailapp.data.repository.EmailResolutionResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmailDetailViewModel(
    private val emailId: String,
    private val source: EmailDetailEmailSource,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<EmailDetailUiState>(EmailDetailUiState.Loading)
    val uiState: StateFlow<EmailDetailUiState> = _uiState.asStateFlow()

    // ── PDF download state ───────────────────────────────────────
    private val _pdfDownloadStates = MutableStateFlow<Map<String, PdfDownloadState>>(emptyMap())
    val pdfDownloadStates: StateFlow<Map<String, PdfDownloadState>> = _pdfDownloadStates.asStateFlow()
    private val pdfCacheCheckedIds = mutableSetOf<String>()

    // ── PDF open events ──────────────────────────────────────────
    private val _pdfOpenEvents = Channel<PdfExternalActionRequest>(Channel.CONFLATED)
    val pdfOpenEvents: Flow<PdfExternalActionRequest> = _pdfOpenEvents.receiveAsFlow()

    private val _readFailureEvents = Channel<UiErrorReason>(Channel.BUFFERED)
    val readFailureEvents: Flow<UiErrorReason> = _readFailureEvents.receiveAsFlow()
    private val readOnOpenCoordinator = EmailReadOnOpenCoordinator(source::markAsRead)

    private var pendingActionRequest: PdfExternalActionRequest? = null

    // ── Resolution + body preparation guards ─────────────────────
    private var isResolving = false
    private var isFetchingRemoteBody = false
    private var isFetchingInlineImages = false
    private var cachedInlineImages: Map<String, String>? = null
    private var cachedInlineRefs: List<InlineImageRef>? = null
    private var delivered = false
    private val traceMail = EmailRenderTrace.mailKey(emailId)

    init {
        EmailRenderTrace.d(traceMail, "VM", "VM_INIT")
        resolve()
    }

    // ═══════════════════════════════════════════════════════════════
    // Resolution
    // ═══════════════════════════════════════════════════════════════

    /** Retry a failed resolution. Ignored when resolution is already in flight. */
    fun onRetry() {
        val current = _uiState.value
        if (current !is EmailDetailUiState.ResolutionError || !current.retryable) return
        if (isResolving) return
        resolve()
    }

    /** Retry only the body fetch, keeping the email metadata intact. */
    fun onRetryBody() {
        val current = _uiState.value
        if (current !is EmailDetailUiState.BodyError || !current.retryable) return
        val email = current.email ?: return
        if (isFetchingRemoteBody) return
        isFetchingRemoteBody = true
        // Allow the retry result written to Room to become the new final delivery.
        delivered = false
        _uiState.value = EmailDetailUiState.PreparingBody(email)
        viewModelScope.launch { fetchRemoteBody(emailId, email) }
    }

    private fun resolve() {
        isResolving = true
        _uiState.value = EmailDetailUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = source.resolveById(emailId)) {
                    is EmailResolutionResult.NotFound -> {
                        EmailRenderTrace.d(traceMail, "VM", "VM_STATE_ERROR", "reason=not_found")
                        _uiState.value = EmailDetailUiState.ResolutionError(
                            UiErrorReason.EMAIL_NOT_FOUND, retryable = false
                        )
                    }
                    is EmailResolutionResult.Failure -> {
                        val (reason, retryable) = mapResolutionFailure(result.reason)
                        EmailRenderTrace.d(traceMail, "VM", "VM_STATE_ERROR", "reason=${result.reason.name}")
                        _uiState.value = EmailDetailUiState.ResolutionError(reason, retryable)
                    }
                    is EmailResolutionResult.Found -> {
                        EmailRenderTrace.d(traceMail, "VM", "VM_RESOLVE_FOUND")
                        // Process the returned email immediately
                        handleEmail(result.email)
                        // Start reactive Room observation
                        startRoomObservation()
                    }
                }
            } catch (e: CancellationException) {
                // Resolution was cancelled — propagate but don't show error state
                throw e
            } catch (e: Exception) {
                EmailRenderTrace.d(traceMail, "VM", "VM_STATE_ERROR", "reason=unexpected ${e.javaClass.simpleName}")
                _uiState.value = EmailDetailUiState.ResolutionError(
                    UiErrorReason.EMAIL_RESOLUTION_FAILED, retryable = true
                )
            } finally {
                isResolving = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Room observation (after Found)
    // ═══════════════════════════════════════════════════════════════

    private fun startRoomObservation() {
        viewModelScope.launch {
            source.observe(emailId).collect { email ->
                if (email != null) {
                    handleEmail(email)
                } else {
                    // Null emission after resolution — ignore (this is the bug fix)
                    EmailRenderTrace.d(traceMail, "VM", "VM_NULL_AFTER_RESOLVE")
                }
            }
        }
    }

    private suspend fun handleEmail(email: Email) {
        // Mark as read (once, coordinated)
        readOnOpenCoordinator.prepare(email)?.let { markAsRead ->
            viewModelScope.launch {
                when (val outcome = markAsRead()) {
                    EmailReadOnOpenOutcome.Marked -> Unit
                    is EmailReadOnOpenOutcome.Failure -> {
                        ensureActive()
                        _readFailureEvents.send(outcome.reason)
                    }
                }
            }
        }

        EmailRenderTrace.d(
            traceMail, "VM", "VM_ROOM_EMIT",
            "bodyBlank=${email.body.isBlank()} cleanBlank=${email.cleanBody.isBlank()} " +
                "bodyLen=${email.body.length} delivered=$delivered " +
                "remoteFetching=$isFetchingRemoteBody inlineFetching=$isFetchingInlineImages"
        )

        val baseHtml = if (email.cleanBody.isNotBlank()) email.cleanBody else email.body
        val needsStablePdfIdentity = email.pdfAttachments.any { it.partId.isNullOrBlank() }
        val needsRemoteFetch = (
            baseHtml.isBlank() ||
                !email.pdfMetadataScanned ||
                needsStablePdfIdentity
            )

        // Check each unseen MIME part's PDF cache
        val uncheckedAttachments = email.pdfAttachments.filter {
            pdfCacheCheckedIds.add(it.stableId)
        }
        if (email.pdfMetadataScanned && uncheckedAttachments.isNotEmpty()) {
            EmailRenderTrace.d(traceMail, "VM", "VM_PDF_CACHE_CHECK", "count=${uncheckedAttachments.size}")
            uncheckedAttachments.forEach { attachment ->
                viewModelScope.launch {
                    val stableId = attachment.stableId
                    val cached = withContext(workerDispatcher) {
                        source.checkPdfCache(emailId, stableId)
                    }
                    ensureActive()
                    val restoredState = cached ?: PdfDownloadState.Idle
                    _pdfDownloadStates.update { states ->
                        when (states[stableId]) {
                            null, PdfDownloadState.Idle -> states + (stableId to restoredState)
                            else -> states
                        }
                    }
                }
            }
        }

        when {
            needsRemoteFetch && !isFetchingRemoteBody && !delivered -> {
                isFetchingRemoteBody = true
                EmailRenderTrace.d(traceMail, "VM", "VM_STATE_PREPARING", "reason=remote_body")
                if (!delivered) {
                    _uiState.value = EmailDetailUiState.PreparingBody(email)
                }
                viewModelScope.launch { fetchRemoteBody(emailId, email) }
            }
            baseHtml.isNotBlank() && baseHtml.contains("cid:", ignoreCase = true) &&
                cachedInlineImages == null && !isFetchingInlineImages -> {
                isFetchingInlineImages = true
                if (!delivered) {
                    delivered = true
                    EmailRenderTrace.d(traceMail, "VM", "VM_STATE_READY",
                        "source=clean_body_pending_inline bodyLen=${baseHtml.length} " +
                            "bodyKey=${EmailRenderTrace.bodyKey(baseHtml)}")
                    _uiState.value = EmailDetailUiState.Ready(
                        email.copy(body = baseHtml), inlineImagesLoading = true
                    )
                }
                viewModelScope.launch { resolveInlineImages(emailId, email.copy(body = baseHtml)) }
            }
            baseHtml.isNotBlank() && (!baseHtml.contains("cid:", ignoreCase = true) || cachedInlineImages != null) -> {
                if (delivered && !isFetchingInlineImages) {
                    EmailRenderTrace.d(traceMail, "VM", "VM_IGNORED_EMISSION", "reason=delivered")
                    return
                }
                val inlineImages = cachedInlineImages ?: emptyMap()
                val injectedBody = if (inlineImages.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        source.injectInlineImages(baseHtml, inlineImages)
                    }
                } else baseHtml
                val finalEmail = email.copy(body = injectedBody)
                delivered = true
                EmailRenderTrace.d(traceMail, "VM", "VM_STATE_READY",
                    "source=ready_final bodyLen=${finalEmail.body.length} " +
                        "bodyKey=${EmailRenderTrace.bodyKey(finalEmail.body)}")
                _uiState.value = EmailDetailUiState.Ready(finalEmail, inlineImagesLoading = false)
            }
            else -> {
                if (!delivered) {
                    _uiState.value = EmailDetailUiState.PreparingBody(email)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Remote body fetch
    // ═══════════════════════════════════════════════════════════════

    private suspend fun fetchRemoteBody(emailId: String, email: Email) {
        val startedAt = EmailRenderTrace.now()
        EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_START")
        try {
            val fetchedResult = withContext(workerDispatcher) {
                source.fetchAndCacheBody(emailId)
            }
            currentCoroutineContext().ensureActive()
            when {
                fetchedResult == null -> {
                    if (!delivered) {
                        delivered = true
                        EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_FAILURE",
                            "reason=null_result durationMs=${EmailRenderTrace.now() - startedAt}")
                        _uiState.value = EmailDetailUiState.BodyError(
                            email, UiErrorReason.EMAIL_BODY_LOAD_FAILED, retryable = true
                        )
                    }
                }
                fetchedResult.rawBody.isNullOrBlank() && fetchedResult.pdfAttachments.isEmpty() -> {
                    if (!delivered) {
                        delivered = true
                        EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_FAILURE",
                            "reason=empty_body_no_pdfs durationMs=${EmailRenderTrace.now() - startedAt}")
                        _uiState.value = EmailDetailUiState.BodyError(
                            email, UiErrorReason.EMAIL_BODY_LOAD_FAILED, retryable = false
                        )
                    }
                }
                fetchedResult.rawBody.isNullOrBlank() && fetchedResult.pdfAttachments.isNotEmpty() -> {
                    if (!delivered) {
                        delivered = true
                        val pdfEmail = email.copy(
                            pdfAttachments = fetchedResult.pdfAttachments, pdfMetadataScanned = true
                        )
                        EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_PDF_METADATA",
                            "pdfCount=${fetchedResult.pdfAttachments.size} " +
                                "durationMs=${EmailRenderTrace.now() - startedAt}")
                        _uiState.value = EmailDetailUiState.BodyError(
                            pdfEmail, UiErrorReason.EMAIL_BODY_PDFS_ONLY, retryable = false
                        )
                    }
                }
                else -> {
                    cachedInlineRefs = fetchedResult.inlineRefs
                    EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_SUCCESS",
                        "durationMs=${EmailRenderTrace.now() - startedAt} refs=${cachedInlineRefs?.size ?: 0}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            if (!delivered) {
                delivered = true
                EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_FAILURE",
                    "reason=${error.javaClass.simpleName} durationMs=${EmailRenderTrace.now() - startedAt}")
                _uiState.value = EmailDetailUiState.BodyError(
                    email, UiErrorReason.EMAIL_BODY_LOAD_FAILED, retryable = true
                )
            }
        } finally {
            isFetchingRemoteBody = false
            EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_END",
                "durationMs=${EmailRenderTrace.now() - startedAt}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Inline image resolution
    // ═══════════════════════════════════════════════════════════════

    private suspend fun resolveInlineImages(emailId: String, email: Email) {
        val startedAt = EmailRenderTrace.now()
        EmailRenderTrace.d(traceMail, "VM", "VM_INLINE_START",
            "bodyLen=${email.body.length} bodyKey=${EmailRenderTrace.bodyKey(email.body)}")
        try {
            val refs = cachedInlineRefs ?: withContext(workerDispatcher) {
                source.fetchAndCacheBody(emailId)?.inlineRefs
            } ?: emptyList()
            currentCoroutineContext().ensureActive()

            val images = if (refs.isNotEmpty()) {
                withContext(workerDispatcher) { source.downloadInlineImages(emailId, refs) }
            } else emptyMap()
            currentCoroutineContext().ensureActive()

            val injectedBody = if (images.isNotEmpty()) {
                withContext(Dispatchers.Default) { source.injectInlineImages(email.body, images) }
            } else email.body
            currentCoroutineContext().ensureActive()

            cachedInlineImages = images
            val injectedEmail = email.copy(body = injectedBody)
            EmailRenderTrace.d(traceMail, "VM", "VM_INLINE_SUCCESS",
                "count=${images.size} finalLen=${injectedBody.length} " +
                    "finalKey=${EmailRenderTrace.bodyKey(injectedBody)} " +
                    "durationMs=${EmailRenderTrace.now() - startedAt}")
            EmailRenderTrace.d(traceMail, "VM", "VM_STATE_READY",
                "source=inline_success bodyLen=${injectedBody.length} " +
                    "bodyKey=${EmailRenderTrace.bodyKey(injectedBody)}")
            _uiState.value = EmailDetailUiState.Ready(injectedEmail, inlineImagesLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            cachedInlineImages = emptyMap()
            EmailRenderTrace.d(traceMail, "VM", "VM_INLINE_FAILURE",
                "reason=${error.javaClass.simpleName} durationMs=${EmailRenderTrace.now() - startedAt}")
            EmailRenderTrace.d(traceMail, "VM", "VM_STATE_READY",
                "source=inline_fallback bodyLen=${email.body.length} " +
                    "bodyKey=${EmailRenderTrace.bodyKey(email.body)}")
            _uiState.value = EmailDetailUiState.Ready(email, inlineImagesLoading = false)
        } finally {
            isFetchingInlineImages = false
            EmailRenderTrace.d(traceMail, "VM", "VM_INLINE_END",
                "durationMs=${EmailRenderTrace.now() - startedAt}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PDF download
    // ═══════════════════════════════════════════════════════════════

    fun onPdfAttachmentClick(attachment: PdfAttachmentMetadata) {
        enqueueExternalAction(
            PdfExternalActionRequest.Open(emailId, attachment.stableId, attachment.fileName),
            attachment
        )
    }

    fun onPdfSaveClick(attachment: PdfAttachmentMetadata) {
        enqueueExternalAction(
            PdfExternalActionRequest.Save(emailId, attachment.stableId, attachment.fileName),
            attachment
        )
    }

    private fun enqueueExternalAction(request: PdfExternalActionRequest, attachment: PdfAttachmentMetadata) {
        val currentState = _pdfDownloadStates.value[attachment.stableId]
        when (currentState) {
            null, is PdfDownloadState.Idle, is PdfDownloadState.Error -> {
                pendingActionRequest = request
                downloadPdf(attachment)
            }
            is PdfDownloadState.Ready -> {
                pendingActionRequest = null
                _pdfOpenEvents.trySend(request)
            }
            PdfDownloadState.Downloading -> { /* no-op */ }
        }
    }

    private fun downloadPdf(attachment: PdfAttachmentMetadata) {
        val stableId = attachment.stableId
        _pdfDownloadStates.update { it + (stableId to PdfDownloadState.Downloading) }
        viewModelScope.launch {
            EmailRenderTrace.d(traceMail, "VM", "VM_PDF_DOWNLOAD_START",
                "attachmentId=${attachment.attachmentId} fileName=${attachment.fileName}")
            val state = source.downloadPdf(emailId, attachment)
            ensureActive()
            _pdfDownloadStates.update { it + (stableId to state) }
            EmailRenderTrace.d(traceMail, "VM", "VM_PDF_DOWNLOAD_END",
                "attachmentId=${attachment.attachmentId} state=${state::class.simpleName}")
            if (state is PdfDownloadState.Ready) {
                pendingActionRequest?.let { pending ->
                    if (pending.stablePartId == stableId) {
                        pendingActionRequest = null
                        _pdfOpenEvents.trySend(pending)
                    }
                }
            }
        }
    }

    fun onPdfCacheExpired(stablePartId: String) {
        _pdfDownloadStates.update { it + (stablePartId to PdfDownloadState.Idle) }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    companion object {
        fun mapResolutionFailure(reason: EmailResolutionFailureReason): Pair<UiErrorReason, Boolean> = when (reason) {
            EmailResolutionFailureReason.NO_ACTIVE_ACCOUNT -> UiErrorReason.NO_ACTIVE_ACCOUNT to false
            EmailResolutionFailureReason.SESSION_CHANGED -> UiErrorReason.ACCOUNT_CHANGED to false
            EmailResolutionFailureReason.SESSION_EXPIRED -> UiErrorReason.SESSION_EXPIRED to false
            EmailResolutionFailureReason.INVALID_ID -> UiErrorReason.EMAIL_INVALID_REFERENCE to false
            EmailResolutionFailureReason.REMOTE_REJECTED -> UiErrorReason.EMAIL_ACCESS_DENIED to false
            EmailResolutionFailureReason.NO_CONNECTION -> UiErrorReason.NO_CONNECTION to true
            EmailResolutionFailureReason.TEMPORARY_REMOTE -> UiErrorReason.EMAIL_TEMPORARILY_UNAVAILABLE to true
            EmailResolutionFailureReason.INVALID_RESPONSE -> UiErrorReason.EMAIL_RESOLUTION_FAILED to true
            EmailResolutionFailureReason.LOCAL_READ_FAILED -> UiErrorReason.EMAIL_LOCAL_CACHE_FAILED to true
            EmailResolutionFailureReason.LOCAL_WRITE_FAILED -> UiErrorReason.EMAIL_LOCAL_CACHE_FAILED to true
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory
    // ═══════════════════════════════════════════════════════════════

    class Factory(
        private val emailId: String,
        private val source: EmailDetailEmailSource,
        private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EmailDetailViewModel::class.java)) {
                return EmailDetailViewModel(emailId, source, workerDispatcher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
