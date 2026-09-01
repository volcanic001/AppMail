package com.david.mailapp.feature.emaildetail

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.core.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailDetailRoute(
    emailId: String,
    onBack: () -> Unit,
    onReply: (String) -> Unit,
    onForward: (String) -> Unit,
    modifier: Modifier
) {
    val repository = AppContainer.emailRepository
    val source = RepositoryEmailDetailSource(repository)
    val viewModel: EmailDetailViewModel = viewModel(
        key = emailId,
        factory = EmailDetailViewModel.Factory(emailId, source)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfDownloadStates by viewModel.pdfDownloadStates.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    EmailDetailReadFailureEffect(
        failureEvents = viewModel.readFailureEvents,
        snackbarHostState = snackbarHostState,
        stringProvider = AppContainer.stringProvider
    )

    val savingState = rememberEmailDetailPdfEffects(
        viewModel = viewModel,
        lifecycleOwner = lifecycleOwner,
        snackbarHostState = snackbarHostState
    )

    // ── Traces ─────────────────────────────────────────────────
    val traceMail = remember(emailId) { EmailRenderTrace.mailKey(emailId) }

    DisposableEffect(traceMail) {
        EmailRenderTrace.d(traceMail, "UI", "UI_SCREEN_ENTER")
        onDispose {
            EmailRenderTrace.d(traceMail, "UI", "UI_SCREEN_DISPOSE")
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.onScreenDisposed(emailId)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is EmailDetailUiState.Ready -> {
                com.david.mailapp.core.perf.MailOpenPerformanceTrace.onEmailReady(emailId)
            }
            is EmailDetailUiState.ResolutionError -> {
                com.david.mailapp.core.perf.MailOpenPerformanceTrace.onError(
                    emailId,
                    "resolution_error_${state.reason.name}"
                )
            }
            is EmailDetailUiState.BodyError -> {
                com.david.mailapp.core.perf.MailOpenPerformanceTrace.onError(
                    emailId,
                    "body_error_${state.reason.name}"
                )
            }
            else -> Unit
        }

        val details = when (val state = uiState) {
            EmailDetailUiState.Loading -> "state=Loading"
            is EmailDetailUiState.ResolutionError ->
                "state=ResolutionError reason=${state.reason.name} retryable=${state.retryable}"
            is EmailDetailUiState.PreparingBody ->
                "state=PreparingBody metadataBodyLen=${state.email.body.length} " +
                    "metadataBodyKey=${EmailRenderTrace.bodyKey(state.email.body)}"
            is EmailDetailUiState.Ready ->
                "state=Ready bodyLen=${state.email.body.length} " +
                    "bodyKey=${EmailRenderTrace.bodyKey(state.email.body)}"
            is EmailDetailUiState.BodyError ->
                "state=BodyError hasMetadata=${state.email != null}"
        }
        EmailRenderTrace.d(traceMail, "UI", "UI_STATE_CHANGED", details)
    }

    EmailDetailPresentation(
        uiState = uiState,
        pdfDownloadStates = pdfDownloadStates,
        savingStableIds = savingState.value,
        traceMail = traceMail,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onReply = onReply,
        onForward = onForward,
        onRetry = viewModel::onRetry,
        onRetryBody = viewModel::onRetryBody,
        onPdfAttachmentClick = viewModel::onPdfAttachmentClick,
        onPdfSaveClick = viewModel::onPdfSaveClick,
        modifier = modifier
    )
}
