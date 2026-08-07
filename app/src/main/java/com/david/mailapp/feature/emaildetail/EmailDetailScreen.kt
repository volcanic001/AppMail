package com.david.mailapp.feature.emaildetail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.feature.emaildetail.components.EmailDetailContent
import com.david.mailapp.feature.emaildetail.components.EmailDetailTopBar
import com.david.mailapp.feature.emaildetail.components.FloatingHeaderPanel
import com.david.mailapp.feature.emaildetail.components.FullscreenImageDialog
import com.david.mailapp.feature.emaildetail.components.ImageActionSheet
import com.david.mailapp.feature.emaildetail.components.EmailDetailBodyError
import com.david.mailapp.feature.emaildetail.components.EmailDetailLoading
import com.david.mailapp.feature.emaildetail.components.EmailDetailResolutionError
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

private const val TAG = "EmailDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    onBack: () -> Unit,
    onReply: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    modifier: Modifier = Modifier
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
    val screenContext = LocalContext.current
    val scope = rememberCoroutineScope()

    EmailDetailReadFailureEffect(
        failureEvents = viewModel.readFailureEvents,
        snackbarHostState = snackbarHostState,
        stringProvider = AppContainer.stringProvider
    )

    // ── PDF Save state ───────────────────────────────────────────
    val savingStableIds = remember { mutableSetOf<String>() }
    val savingState = remember { mutableStateOf(savingStableIds.toSet()) }

    var savedSaveEmailId by rememberSaveable { mutableStateOf("") }
    var savedSaveStableId by rememberSaveable { mutableStateOf("") }
    var savedSaveDisplayName by rememberSaveable { mutableStateOf("") }

    // ── Resolved labels for PDF callbacks (non-Composable helpers) ──
    val pdfLabels = PdfActionLabels(
        cacheExpired = stringResource(R.string.pdf_cache_expired),
        saved = stringResource(R.string.pdf_saved),
        saveFailed = stringResource(R.string.pdf_save_failed),
        noFilePicker = stringResource(R.string.pdf_no_file_picker),
        pickerOpenFailed = stringResource(R.string.pdf_picker_open_failed),
        noViewer = stringResource(R.string.pdf_no_viewer),
        openFailed = stringResource(R.string.pdf_open_failed)
    )
    val defaultPdfFilename = stringResource(R.string.pdf_default_filename)

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) {
            // User cancelled
            savedSaveEmailId = ""
            savedSaveStableId = ""
            savedSaveDisplayName = ""
            return@rememberLauncherForActivityResult
        }
        val mailId = savedSaveEmailId
        val stableId = savedSaveStableId
        savedSaveEmailId = ""
        savedSaveStableId = ""
        savedSaveDisplayName = ""

        savingStableIds.add(stableId)
        savingState.value = savingStableIds.toSet()

        scope.launch {
            val repository = com.david.mailapp.core.di.AppContainer.emailRepository
            val file = withContext(Dispatchers.IO) {
                repository.getValidatedCachedPdf(mailId, stableId)
            }
            if (file == null) {
                viewModel.onPdfCacheExpired(stableId)
                snackbarHostState.showSnackbar(
                    message = pdfLabels.cacheExpired
                )
                savingStableIds.remove(stableId)
                savingState.value = savingStableIds.toSet()
                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                copyFileToUri(screenContext, file, uri)
            }
            if (success) {
                snackbarHostState.showSnackbar(
                    message = pdfLabels.saved
                )
            } else {
                // Try to clean up partial document
                try {
                    screenContext.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete partial SAF document", e)
                }
                snackbarHostState.showSnackbar(
                    message = pdfLabels.saveFailed
                )
            }
            savingStableIds.remove(stableId)
            savingState.value = savingStableIds.toSet()
        }
    }

    // ── PDF open event collection ────────────────────────────────
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pdfOpenEvents.collect { request ->
                when (request) {
                    is PdfExternalActionRequest.Open -> {
                        handlePdfExternalActionRequest(
                            context = screenContext,
                            request = request,
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            labels = pdfLabels,
                            defaultPdfFilename = defaultPdfFilename
                        )
                    }
                    is PdfExternalActionRequest.Save -> {
                        savedSaveEmailId = request.emailId
                        savedSaveStableId = request.stablePartId
                        savedSaveDisplayName = request.displayName
                        val suggestedName = buildPdfSuggestedName(request.displayName, defaultPdfFilename)
                        try {
                            savePdfLauncher.launch(suggestedName)
                        } catch (_: ActivityNotFoundException) {
                            savedSaveEmailId = ""
                            savedSaveStableId = ""
                            savedSaveDisplayName = ""
                            snackbarHostState.showSnackbar(
                                message = pdfLabels.noFilePicker
                            )
                        } catch (_: SecurityException) {
                            savedSaveEmailId = ""
                            savedSaveStableId = ""
                            savedSaveDisplayName = ""
                            snackbarHostState.showSnackbar(
                                message = pdfLabels.pickerOpenFailed
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Overlay state ───────────────────────────────────────────
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var showFullscreenImage by remember { mutableStateOf<String?>(null) }
    var showDetailsPanel by remember { mutableStateOf(false) }
    val traceMail = remember(emailId) { EmailRenderTrace.mailKey(emailId) }

    DisposableEffect(traceMail) {
        EmailRenderTrace.d(traceMail, "UI", "UI_SCREEN_ENTER")
        onDispose {
            EmailRenderTrace.d(traceMail, "UI", "UI_SCREEN_DISPOSE")
        }
    }

    LaunchedEffect(uiState) {
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

    // Close details panel on back press; second back press pops the screen.
    BackHandler(enabled = showDetailsPanel) {
        showDetailsPanel = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            EmailDetailTopBar(
                uiState = uiState,
                onBack = onBack,
                onReply = onReply,
                onForward = onForward
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // ── Main content (WebView fills entire space) ─────────
            when (val state = uiState) {
                EmailDetailUiState.Loading -> {
                    EmailDetailLoading(Modifier.fillMaxSize())
                }

                is EmailDetailUiState.ResolutionError -> {
                    EmailDetailResolutionError(
                        state = state,
                        onRetry = viewModel::onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is EmailDetailUiState.BodyError -> {
                    EmailDetailBodyError(
                        state = state,
                        pdfDownloadStates = pdfDownloadStates,
                        onPdfAttachmentClick = viewModel::onPdfAttachmentClick,
                        onPdfSaveClick = viewModel::onPdfSaveClick,
                        savingStableIds = savingState.value,
                        onRetry = viewModel::onRetryBody,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is EmailDetailUiState.PreparingBody, is EmailDetailUiState.Ready -> {
                    val email = when (state) {
                        is EmailDetailUiState.PreparingBody -> state.email
                        is EmailDetailUiState.Ready -> state.email
                        else -> throw IllegalStateException()
                    }
                    val body = (state as? EmailDetailUiState.Ready)?.email?.body
                    EmailDetailContent(
                        email = email,
                        body = body,
                        traceMail = traceMail,
                        pdfDownloadStates = pdfDownloadStates,
                        onPdfAttachmentClick = viewModel::onPdfAttachmentClick,
                        onPdfSaveClick = viewModel::onPdfSaveClick,
                        savingStableIds = savingState.value,
                        onImageLongPress = { activeImageUrl = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ── Floating header panel (overlay, zIndex 2) ─────────
            val emailForPanel = (uiState as? EmailDetailUiState.PreparingBody)?.email
                ?: (uiState as? EmailDetailUiState.Ready)?.email

            if (emailForPanel != null) {
                // Scrim: blocks touches to WebView when panel is open
                if (showDetailsPanel) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                            .clickable { showDetailsPanel = false }
                            .zIndex(1f)
                    )
                }

                // The floating panel itself always mounted, controls own visibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                ) {
                    FloatingHeaderPanel(
                        email = emailForPanel,
                        isExpanded = showDetailsPanel,
                        onToggle = { showDetailsPanel = !showDetailsPanel },
                        traceMail = traceMail,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Image action sheet (overlay) ───────────────────────
            activeImageUrl?.let { imageUrl ->
                ImageActionSheet(
                    activeImageUrl = imageUrl,
                    onOpenFullscreen = { showFullscreenImage = it },
                    onDismiss = { activeImageUrl = null }
                )
            }
        }

        // ── Fullscreen image dialog (outside content Box) ──────
        showFullscreenImage?.let { imageUrl ->
            FullscreenImageDialog(
                imageUrl = imageUrl,
                onDismiss = { showFullscreenImage = null }
            )
        }
    }
}
