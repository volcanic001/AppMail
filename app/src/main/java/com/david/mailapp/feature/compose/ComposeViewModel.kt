package com.david.mailapp.feature.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.david.mailapp.core.auth.AuthManager
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.data.repository.EmailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        // Set mode-dependent fields synchronously — no delay for UI.
        _uiState.value = when (args) {
            is ComposeArgs.Write -> ComposeUiState(
                composeMode = ComposeMode.WRITE
            )
            is ComposeArgs.Reply -> {
                val email = args.originalEmail
                ComposeUiState(
                    toField = ComposeFormatUtils.extractEmailAddress(email.from),
                    subject = composeFormatUtils.buildReplySubject(email.subject),
                    originalEmail = email,
                    composeMode = ComposeMode.REPLY
                )
            }
            is ComposeArgs.Forward -> {
                val email = args.originalEmail
                ComposeUiState(
                    subject = composeFormatUtils.buildForwardSubject(email.subject),
                    originalEmail = email,
                    composeMode = ComposeMode.FORWARD
                )
            }
        }

        // Only the sender address requires a suspend call — load it async.
        viewModelScope.launch {
            val fromAddress = emailSource.getUserEmail().orEmpty()
            _uiState.value = _uiState.value.copy(fromAddress = fromAddress)
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
