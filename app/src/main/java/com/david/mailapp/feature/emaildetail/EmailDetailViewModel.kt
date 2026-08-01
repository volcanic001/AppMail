package com.david.mailapp.feature.emaildetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.data.remote.provider.InlineImageRef
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import kotlinx.coroutines.CancellationException
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
    private val repository: EmailRepository
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
    private val readOnOpenCoordinator = EmailReadOnOpenCoordinator(repository::markAsRead)

    /**
     * Última solicitud externa (Open / Save) que espera a que termine la
     * descarga. Al completarse, solo la última solicitud se ejecuta.
     */
    private var pendingActionRequest: PdfExternalActionRequest? = null

    private var isFetchingRemoteBody = false
    private var isFetchingInlineImages = false
    private var cachedInlineImages: Map<String, String>? = null
    private var cachedInlineRefs: List<InlineImageRef>? = null
    private var delivered = false
    private val traceMail = EmailRenderTrace.mailKey(emailId)

    init {
        EmailRenderTrace.d(traceMail, "VM", "VM_INIT")
        viewModelScope.launch {
            repository.getEmailById(emailId).collect { email ->
                if (email != null) {
                    readOnOpenCoordinator.prepare(email)?.let { markAsRead ->
                        launch {
                            when (val outcome = markAsRead()) {
                                EmailReadOnOpenOutcome.Marked -> Unit
                                is EmailReadOnOpenOutcome.Failure ->
                                    _readFailureEvents.send(outcome.reason)
                            }
                        }
                    }

                    EmailRenderTrace.d(
                        traceMail,
                        "VM",
                        "VM_ROOM_EMIT",
                        "bodyBlank=${email.body.isBlank()} cleanBlank=${email.cleanBody.isBlank()} " +
                            "bodyLen=${email.body.length} delivered=$delivered " +
                            "remoteFetching=$isFetchingRemoteBody inlineFetching=$isFetchingInlineImages"
                    )

                    val baseHtml = if (email.cleanBody.isNotBlank()) email.cleanBody else email.body
                    val needsStablePdfIdentity = email.pdfAttachments.any {
                        it.partId.isNullOrBlank()
                    }
                    val needsRemoteFetch = (
                        baseHtml.isBlank() ||
                            !email.pdfMetadataScanned ||
                            needsStablePdfIdentity
                        )

                    // Check each stable MIME part once. A later Room emission may
                    // replace legacy metadata with a newly available partId.
                    val uncheckedAttachments = email.pdfAttachments.filter {
                        pdfCacheCheckedIds.add(it.stableId)
                    }
                    if (email.pdfMetadataScanned && uncheckedAttachments.isNotEmpty()) {
                        EmailRenderTrace.d(
                            traceMail,
                            "VM",
                            "VM_PDF_CACHE_CHECK",
                            "count=${uncheckedAttachments.size}"
                        )
                        uncheckedAttachments.forEach { attachment ->
                            launch {
                                val stableId = attachment.stableId
                                val cached = withContext(Dispatchers.IO) {
                                    repository.checkPdfCache(emailId, stableId)
                                }
                                val restoredState = cached ?: PdfDownloadState.Idle
                                _pdfDownloadStates.update { states ->
                                    when (states[stableId]) {
                                        null, PdfDownloadState.Idle ->
                                            states + (stableId to restoredState)
                                        else -> states
                                    }
                                }
                            }
                        }
                    }

                    when {
                        // Body is blank or PDF metadata not yet scanned → need remote fetch
                        // Only fetch once — delivered prevents re-fetch after BodyError
                        needsRemoteFetch && !isFetchingRemoteBody && !delivered -> {
                            isFetchingRemoteBody = true
                            EmailRenderTrace.d(
                                traceMail,
                                "VM",
                                "VM_STATE_PREPARING",
                                "reason=remote_body"
                            )
                            if (!delivered) {
                                _uiState.value = EmailDetailUiState.PreparingBody(email)
                            }
                            launch { fetchRemoteBody(emailId, email, repository) }
                        }
                        // Body available, but contains cid: and inline images not yet resolved
                        baseHtml.isNotBlank() && baseHtml.contains("cid:", ignoreCase = true) && cachedInlineImages == null && !isFetchingInlineImages -> {
                            isFetchingInlineImages = true
                            // Opt #4: Mostrar correo primero sin imágenes (HTML limpio)
                            if (!delivered) {
                                delivered = true
                                EmailRenderTrace.d(
                                    traceMail,
                                    "VM",
                                    "VM_STATE_READY",
                                    "source=clean_body_pending_inline bodyLen=${baseHtml.length} " +
                                        "bodyKey=${EmailRenderTrace.bodyKey(baseHtml)}"
                                )
                                _uiState.value = EmailDetailUiState.Ready(email.copy(body = baseHtml), inlineImagesLoading = true)
                            }
                            launch { resolveInlineImages(emailId, email.copy(body = baseHtml), repository) }
                        }
                        // Body available, either no cid: OR inline images already resolved
                        baseHtml.isNotBlank() && (!baseHtml.contains("cid:", ignoreCase = true) || cachedInlineImages != null) -> {
                            if (delivered && !isFetchingInlineImages) {
                                EmailRenderTrace.d(traceMail, "VM", "VM_IGNORED_EMISSION", "reason=delivered")
                                return@collect
                            }
                            val inlineImages = cachedInlineImages ?: emptyMap()
                            val injectedBody = if (inlineImages.isNotEmpty()) {
                                withContext(Dispatchers.Default) {
                                    repository.injectInlineImages(baseHtml, inlineImages)
                                }
                            } else baseHtml

                            val finalEmail = email.copy(body = injectedBody)
                            delivered = true
                            EmailRenderTrace.d(
                                traceMail,
                                "VM",
                                "VM_STATE_READY",
                                "source=ready_final bodyLen=${finalEmail.body.length} " +
                                    "bodyKey=${EmailRenderTrace.bodyKey(finalEmail.body)}"
                            )
                            _uiState.value = EmailDetailUiState.Ready(finalEmail, inlineImagesLoading = false)
                        }
                        else -> {
                            if (!delivered) {
                                _uiState.value = EmailDetailUiState.PreparingBody(email)
                            }
                        }
                    }
                } else {
                    if (!delivered) {
                        delivered = true
                        EmailRenderTrace.d(traceMail, "VM", "VM_STATE_ERROR", "reason=email_not_found")
                        _uiState.value = EmailDetailUiState.BodyError(null, UiErrorReason.EMAIL_NOT_FOUND)
                    }
                }
            }
        }
    }

    private suspend fun fetchRemoteBody(emailId: String, email: Email, repository: EmailRepository) {
        val startedAt = EmailRenderTrace.now()
        EmailRenderTrace.d(traceMail, "VM", "VM_REMOTE_START")
        try {
            val fetchedResult = withContext(Dispatchers.IO) {
                repository.fetchAndCacheBody(emailId)
            }
            if (fetchedResult == null) {
                if (!delivered) {
                    delivered = true
                    EmailRenderTrace.d(
                        traceMail,
                        "VM",
                        "VM_REMOTE_FAILURE",
                        "reason=null_result durationMs=${EmailRenderTrace.now() - startedAt}"
                    )
                    _uiState.value = EmailDetailUiState.BodyError(email, UiErrorReason.EMAIL_BODY_LOAD_FAILED)
                }
            } else if (fetchedResult.rawBody.isNullOrBlank() && fetchedResult.pdfAttachments.isEmpty()) {
                // No body AND no PDFs → genuinely empty
                if (!delivered) {
                    delivered = true
                    EmailRenderTrace.d(
                        traceMail,
                        "VM",
                        "VM_REMOTE_FAILURE",
                        "reason=empty_body_no_pdfs durationMs=${EmailRenderTrace.now() - startedAt}"
                    )
                    _uiState.value = EmailDetailUiState.BodyError(email, UiErrorReason.EMAIL_BODY_LOAD_FAILED)
                }
            } else if (fetchedResult.rawBody.isNullOrBlank() && fetchedResult.pdfAttachments.isNotEmpty()) {
                // No body but PDF metadata was found — deliver BodyError with metadata preserved
                if (!delivered) {
                    delivered = true
                    val pdfEmail = email.copy(
                        pdfAttachments = fetchedResult.pdfAttachments,
                        pdfMetadataScanned = true
                    )
                    EmailRenderTrace.d(
                        traceMail,
                        "VM",
                        "VM_REMOTE_PDF_METADATA",
                        "pdfCount=${fetchedResult.pdfAttachments.size} durationMs=${EmailRenderTrace.now() - startedAt}"
                    )
                    _uiState.value = EmailDetailUiState.BodyError(pdfEmail, UiErrorReason.EMAIL_BODY_PDFS_ONLY)
                }
            } else {
                cachedInlineRefs = fetchedResult.inlineRefs
                EmailRenderTrace.d(
                    traceMail,
                    "VM",
                    "VM_REMOTE_SUCCESS",
                    "durationMs=${EmailRenderTrace.now() - startedAt} refs=${cachedInlineRefs?.size ?: 0} pdfs=${fetchedResult.pdfAttachments.size}"
                )
            }
            // On success Room will re-emit with the fetched body — the collect
            // lambda will pick it up and proceed to inline-image resolution.
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            if (!delivered) {
                delivered = true
                EmailRenderTrace.d(
                    traceMail,
                    "VM",
                    "VM_REMOTE_FAILURE",
                    "reason=${error.javaClass.simpleName} durationMs=${EmailRenderTrace.now() - startedAt}"
                )
                _uiState.value = EmailDetailUiState.BodyError(email, UiErrorReason.EMAIL_BODY_LOAD_FAILED)
            }
        } finally {
            isFetchingRemoteBody = false
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_REMOTE_END",
                "durationMs=${EmailRenderTrace.now() - startedAt}"
            )
        }
    }

    private suspend fun resolveInlineImages(emailId: String, email: Email, repository: EmailRepository) {
        val startedAt = EmailRenderTrace.now()
        EmailRenderTrace.d(
            traceMail,
            "VM",
            "VM_INLINE_START",
            "bodyLen=${email.body.length} bodyKey=${EmailRenderTrace.bodyKey(email.body)}"
        )
        try {
            val refs = cachedInlineRefs ?: withContext(Dispatchers.IO) {
                repository.fetchAndCacheBody(emailId)?.inlineRefs
            } ?: emptyList()

            val images = if (refs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.downloadInlineImages(emailId, refs)
                }
            } else emptyMap()

            val injectedBody = if (images.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    repository.injectInlineImages(email.body, images)
                }
            } else email.body

            cachedInlineImages = images
            val injectedEmail = email.copy(body = injectedBody)
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_INLINE_SUCCESS",
                "count=${images.size} finalLen=${injectedBody.length} " +
                    "finalKey=${EmailRenderTrace.bodyKey(injectedBody)} " +
                    "durationMs=${EmailRenderTrace.now() - startedAt}"
            )
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_STATE_READY",
                "source=inline_success bodyLen=${injectedBody.length} " +
                    "bodyKey=${EmailRenderTrace.bodyKey(injectedBody)}"
            )
            _uiState.value = EmailDetailUiState.Ready(injectedEmail, inlineImagesLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            // Inline images failed — deliver original body as final fallback.
            cachedInlineImages = emptyMap()
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_INLINE_FAILURE",
                "reason=${error.javaClass.simpleName} durationMs=${EmailRenderTrace.now() - startedAt}"
            )
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_STATE_READY",
                "source=inline_fallback bodyLen=${email.body.length} " +
                    "bodyKey=${EmailRenderTrace.bodyKey(email.body)}"
            )
            _uiState.value = EmailDetailUiState.Ready(email, inlineImagesLoading = false)
        } finally {
            isFetchingInlineImages = false
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_INLINE_END",
                "durationMs=${EmailRenderTrace.now() - startedAt}"
            )
        }
    }

    // ── PDF download ─────────────────────────────────────────────

    /**
     * Maneja el toque sobre una tarjeta PDF: abre el visor externo.
     *
     * - Ready        → emite [PdfExternalActionRequest.Open] inmediatamente.
     * - Idle / Error → registra solicitud pendiente, inicia descarga.
     * - Downloading  → no-op.
     */
    fun onPdfAttachmentClick(attachment: PdfAttachmentMetadata) {
        enqueueExternalAction(
            PdfExternalActionRequest.Open(
                emailId = emailId,
                stablePartId = attachment.stableId,
                displayName = attachment.fileName
            ),
            attachment
        )
    }

    /**
     * Maneja el toque sobre el icono Guardar como: copia el PDF al selector
     * de archivos del sistema.
     *
     * - Ready        → emite [PdfExternalActionRequest.Save] inmediatamente.
     * - Idle / Error → registra solicitud pendiente, inicia descarga.
     * - Downloading  → no-op.
     */
    fun onPdfSaveClick(attachment: PdfAttachmentMetadata) {
        enqueueExternalAction(
            PdfExternalActionRequest.Save(
                emailId = emailId,
                stablePartId = attachment.stableId,
                displayName = attachment.fileName
            ),
            attachment
        )
    }

    private fun enqueueExternalAction(
        request: PdfExternalActionRequest,
        attachment: PdfAttachmentMetadata
    ) {
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
            PdfDownloadState.Downloading -> {
                // No-op
            }
        }
    }

    private fun downloadPdf(attachment: PdfAttachmentMetadata) {
        val attachmentId = attachment.attachmentId
        val stableId = attachment.stableId
        _pdfDownloadStates.update {
            it + (stableId to PdfDownloadState.Downloading)
        }

        viewModelScope.launch {
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_PDF_DOWNLOAD_START",
                "attachmentId=$attachmentId fileName=${attachment.fileName}"
            )
            val state = repository.downloadPdf(emailId, attachment)
            _pdfDownloadStates.update {
                it + (stableId to state)
            }
            EmailRenderTrace.d(
                traceMail,
                "VM",
                "VM_PDF_DOWNLOAD_END",
                "attachmentId=$attachmentId state=${state::class.simpleName}"
            )

            // Auto-open/save si esta descarga era la última solicitada y fue exitosa
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

    /**
     * El archivo en caché desapareció o es inválido entre el toque Ready
     * y la generación del URI para el visor. Resetea a Idle para re-descarga.
     */
    fun onPdfCacheExpired(stablePartId: String) {
        _pdfDownloadStates.update { states ->
            states + (stablePartId to PdfDownloadState.Idle)
        }
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(
        private val emailId: String,
        private val repository: EmailRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EmailDetailViewModel::class.java)) {
                return EmailDetailViewModel(emailId, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
