package com.david.mailapp.feature.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
    private val stringProvider: StringProvider
) : ViewModel() {

    private val composeFormatUtils = ComposeFormatUtils(stringProvider)
    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private var sendJob: Job? = null

    init {
        val initialMode = when (args) {
            is ComposeArgs.Write -> ComposeMode.WRITE
            is ComposeArgs.Reply -> ComposeMode.REPLY
            is ComposeArgs.Forward -> ComposeMode.FORWARD
        }

        _uiState.value = ComposeUiState(
            composeMode = initialMode,
            isLoadingOriginalEmail = initialMode != ComposeMode.WRITE
        )

        viewModelScope.launch {
            val fromAddress = emailSource.getUserEmail().orEmpty()
            _uiState.value = _uiState.value.copy(fromAddress = fromAddress)
        }

        if (initialMode != ComposeMode.WRITE) {
            viewModelScope.launch {
                val originalEmailId = when (args) {
                    is ComposeArgs.Reply -> args.originalEmailId
                    is ComposeArgs.Forward -> args.originalEmailId
                    else -> ""
                }
                try {
                    val email = emailSource.getEmailById(originalEmailId)
                    ensureActive()
                    if (email == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingOriginalEmail = false,
                            originalEmailError = UiErrorReason.EMAIL_NOT_FOUND
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingOriginalEmail = false,
                            originalEmail = email,
                            toField = if (initialMode == ComposeMode.REPLY) {
                                ComposeFormatUtils.extractEmailAddress(email.from)
                            } else {
                                ""
                            },
                            subject = if (initialMode == ComposeMode.REPLY) {
                                composeFormatUtils.buildReplySubject(email.subject)
                            } else {
                                composeFormatUtils.buildForwardSubject(email.subject)
                            }
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
        _uiState.value = _uiState.value.copy(toField = value)
    }

    fun onCcChanged(value: String) {
        _uiState.value = _uiState.value.copy(ccField = value)
    }

    fun onBccChanged(value: String) {
        _uiState.value = _uiState.value.copy(bccField = value)
    }

    fun onSubjectChanged(value: String) {
        _uiState.value = _uiState.value.copy(subject = value)
    }

    fun onBodyChanged(value: String) {
        _uiState.value = _uiState.value.copy(bodyText = value)
    }

    fun onToggleCcBcc() {
        _uiState.value = _uiState.value.copy(isCcBccExpanded = !_uiState.value.isCcBccExpanded)
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
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ComposeViewModel::class.java)) {
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
                return ComposeViewModel(args, emailSource, stringProvider) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
