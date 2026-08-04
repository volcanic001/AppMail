package com.david.mailapp.feature.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.domain.model.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ComposeViewModel(
    private val args: ComposeArgs,
    private val emailSource: ComposeEmailSource,
    private val stringProvider: StringProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        internal const val KEY_MODE = "compose_mode"
        internal const val KEY_ORIGINAL_EMAIL_ID = "compose_original_email_id"
        internal const val KEY_TO = "compose_to"
        internal const val KEY_CC = "compose_cc"
        internal const val KEY_BCC = "compose_bcc"
        internal const val KEY_SUBJECT = "compose_subject"
        internal const val KEY_BODY = "compose_body"
        internal const val KEY_CC_BCC_EXPANDED = "compose_cc_bcc_expanded"
    }

    private val composeFormatUtils = ComposeFormatUtils(stringProvider)
    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private var sendJob: Job? = null

    init {
        val currentMode = when (args) {
            is ComposeArgs.Write -> ComposeMode.WRITE
            is ComposeArgs.Reply -> ComposeMode.REPLY
            is ComposeArgs.Forward -> ComposeMode.FORWARD
        }
        val currentId = when (args) {
            is ComposeArgs.Reply -> args.originalEmailId
            is ComposeArgs.Forward -> args.originalEmailId
            else -> ""
        }

        val hasSavedMode = savedStateHandle.contains(KEY_MODE)
        val hasSavedId = savedStateHandle.contains(KEY_ORIGINAL_EMAIL_ID)

        if (hasSavedMode || hasSavedId) {
            require(hasSavedMode) {
                "Restored email ID requires a restored compose mode"
            }
            val savedMode = savedStateHandle.get<String>(KEY_MODE)
            require(currentMode.name == savedMode) {
                "Restored mode $savedMode incompatible with route $currentMode"
            }

            when (currentMode) {
                ComposeMode.WRITE -> require(!hasSavedId) {
                    "WRITE mode must not restore an original email ID"
                }
                ComposeMode.REPLY, ComposeMode.FORWARD -> {
                    require(hasSavedId) {
                        "$currentMode mode requires a restored original email ID"
                    }
                    val savedId = savedStateHandle.get<String>(KEY_ORIGINAL_EMAIL_ID)
                    require(currentId == savedId) {
                        "Restored email ID $savedId incompatible with route $currentId"
                    }
                }
            }
        } else {
            savedStateHandle[KEY_MODE] = currentMode.name
            if (currentMode != ComposeMode.WRITE) {
                savedStateHandle[KEY_ORIGINAL_EMAIL_ID] = currentId
            }
        }

        val restoredTo = savedStateHandle.get<String>(KEY_TO) ?: ""
        val restoredCc = savedStateHandle.get<String>(KEY_CC) ?: ""
        val restoredBcc = savedStateHandle.get<String>(KEY_BCC) ?: ""
        val restoredSubject = savedStateHandle.get<String>(KEY_SUBJECT) ?: ""
        val restoredBody = savedStateHandle.get<String>(KEY_BODY) ?: ""
        val restoredExpanded = savedStateHandle.get<Boolean>(KEY_CC_BCC_EXPANDED) ?: false

        _uiState.value = ComposeUiState(
            composeMode = currentMode,
            isLoadingOriginalEmail = currentMode != ComposeMode.WRITE,
            toField = restoredTo,
            ccField = restoredCc,
            bccField = restoredBcc,
            subject = restoredSubject,
            bodyText = restoredBody,
            isCcBccExpanded = restoredExpanded
        )

        viewModelScope.launch {
            val fromAddress = emailSource.getUserEmail().orEmpty()
            _uiState.value = _uiState.value.copy(fromAddress = fromAddress)
        }

        if (currentMode != ComposeMode.WRITE) {
            val originalId = when (args) {
                is ComposeArgs.Reply -> args.originalEmailId
                is ComposeArgs.Forward -> args.originalEmailId
                else -> ""
            }

            viewModelScope.launch {
                try {
                    val email = emailSource.getEmailById(originalId)
                    ensureActive()
                    if (email == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingOriginalEmail = false,
                            originalEmailError = UiErrorReason.EMAIL_NOT_FOUND
                        )
                    } else {
                        // Check at application time so edits made while Room was loading
                        // are never overwritten by the Reply/Forward defaults.
                        val shouldFillTo = !savedStateHandle.contains(KEY_TO)
                        val shouldFillSubject = !savedStateHandle.contains(KEY_SUBJECT)
                        val newTo = if (currentMode == ComposeMode.REPLY && shouldFillTo) {
                            // Correos enviados (etiqueta SENT) → responder al destinatario
                            // original; cualquier otro → al remitente. EmailFolder.Other
                            // no se usa porque también agrupa archivados.
                            if (email.labels.contains("SENT")) {
                                // To may contain multiple RFC recipients; preserve the
                                // complete header instead of extracting only the first.
                                email.to.trim()
                            } else {
                                ComposeFormatUtils.extractEmailAddress(email.from)
                            }
                        } else {
                            _uiState.value.toField
                        }
                        val newSubject = if (shouldFillSubject) {
                            when (currentMode) {
                                ComposeMode.REPLY -> composeFormatUtils.buildReplySubject(email.subject)
                                ComposeMode.FORWARD -> composeFormatUtils.buildForwardSubject(email.subject)
                                else -> _uiState.value.subject
                            }
                        } else {
                            _uiState.value.subject
                        }

                        if (shouldFillTo) savedStateHandle[KEY_TO] = newTo
                        if (shouldFillSubject) savedStateHandle[KEY_SUBJECT] = newSubject

                        _uiState.value = _uiState.value.copy(
                            isLoadingOriginalEmail = false,
                            originalEmail = email,
                            toField = newTo,
                            subject = newSubject
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingOriginalEmail = false,
                        originalEmailError = UiErrorReason.UNKNOWN
                    )
                }
            }
        }
    }

    // ── Event handlers ──────────────────────────────────────────

    fun onToChanged(value: String) {
        savedStateHandle[KEY_TO] = value
        _uiState.value = _uiState.value.copy(toField = value)
    }

    fun onCcChanged(value: String) {
        savedStateHandle[KEY_CC] = value
        _uiState.value = _uiState.value.copy(ccField = value)
    }

    fun onBccChanged(value: String) {
        savedStateHandle[KEY_BCC] = value
        _uiState.value = _uiState.value.copy(bccField = value)
    }

    fun onSubjectChanged(value: String) {
        savedStateHandle[KEY_SUBJECT] = value
        _uiState.value = _uiState.value.copy(subject = value)
    }

    fun onBodyChanged(value: String) {
        savedStateHandle[KEY_BODY] = value
        _uiState.value = _uiState.value.copy(bodyText = value)
    }

    fun onToggleCcBcc() {
        val newValue = !_uiState.value.isCcBccExpanded
        savedStateHandle[KEY_CC_BCC_EXPANDED] = newValue
        _uiState.value = _uiState.value.copy(isCcBccExpanded = newValue)
    }

    fun onSend() {
        val state = _uiState.value
        if (state.isLoadingOriginalEmail || state.originalEmailError != null) return
        if (state.toField.isBlank()) return
        if (sendJob?.isActive == true || state.isSending) return

        // Capture immutable snapshot of fields before coroutine launch
        val to = state.toField
        val cc = state.ccField.ifBlank { null }
        val bcc = state.bccField.ifBlank { null }
        val subject = state.subject
        val bodyText = state.bodyText
        val composeMode = state.composeMode
        val originalEmail = state.originalEmail

        // Publish isSending = true and clear sendResult synchronously
        _uiState.value = _uiState.value.copy(isSending = true, sendResult = null)

        sendJob = viewModelScope.launch {
            try {
                val finalBody = when (composeMode) {
                    ComposeMode.WRITE -> bodyText
                    ComposeMode.REPLY -> {
                        if (originalEmail != null) {
                            composeFormatUtils.buildReplyBody(bodyText, originalEmail, originalEmail.snippet)
                        } else bodyText
                    }
                    ComposeMode.FORWARD -> {
                        if (originalEmail != null) {
                            composeFormatUtils.buildForwardBody(bodyText, originalEmail, originalEmail.snippet)
                        } else bodyText
                    }
                }

                val replyContext: ReplyContext? =
                    if (composeMode == ComposeMode.REPLY) {
                        originalEmail?.let(ReplyContext::from)
                    } else null

                emailSource.sendEmail(
                    to = to,
                    cc = cc,
                    bcc = bcc,
                    subject = subject,
                    body = finalBody,
                    replyContext = replyContext
                )

                ensureActive()

                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    sendResult = SendResult.Success
                )
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(isSending = false)
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    sendResult = SendResult.Error(e.toComposeSendErrorReason())
                )
            } finally {
                sendJob = null
            }
        }
    }

    fun onDismissSendResult() {
        _uiState.value = _uiState.value.copy(sendResult = null)
    }

    // ── Factory ──────────────────────────────────────────────────

    class Factory(
        private val args: ComposeArgs,
        private val repository: EmailRepository,
        private val authManager: AuthManager,
        private val stringProvider: StringProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(ComposeViewModel::class.java)) {
                val handle = extras.createSavedStateHandle()
                val emailSource = object : ComposeEmailSource {
                    override suspend fun getUserEmail(): String? = repository.getUserEmail()
                    override suspend fun getEmailById(emailId: String): Email? {
                        return repository.getEmailById(emailId).first()
                    }
                    override suspend fun sendEmail(
                        to: String, cc: String?, bcc: String?,
                        subject: String, body: String,
                        replyContext: ReplyContext?
                    ) = repository.sendEmail(to, cc, bcc, subject, body, replyContext)
                }
                return ComposeViewModel(args, emailSource, stringProvider, handle) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
