package com.david.mailapp.feature.emaildetail

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.david.mailapp.feature.emaildetail.components.EmailDetailBodyError
import com.david.mailapp.feature.emaildetail.components.EmailDetailContent
import com.david.mailapp.feature.emaildetail.components.EmailDetailLoading
import com.david.mailapp.feature.emaildetail.components.EmailDetailResolutionError
import com.david.mailapp.feature.emaildetail.components.EmailDetailTopBar
import com.david.mailapp.feature.emaildetail.components.FloatingHeaderPanel
import com.david.mailapp.feature.emaildetail.components.FullscreenImageDialog
import com.david.mailapp.feature.emaildetail.components.ImageActionSheet
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.PdfAttachmentMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailDetailPresentation(
    uiState: EmailDetailUiState,
    pdfDownloadStates: Map<String, PdfDownloadState>,
    savingStableIds: Set<String>,
    traceMail: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onReply: (String) -> Unit,
    onForward: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryBody: () -> Unit,
    onPdfAttachmentClick: (PdfAttachmentMetadata) -> Unit,
    onPdfSaveClick: (PdfAttachmentMetadata) -> Unit,
    modifier: Modifier
) {
    // ── Overlay state ───────────────────────────────────────────
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var showFullscreenImage by remember { mutableStateOf<String?>(null) }
    var showDetailsPanel by remember { mutableStateOf(false) }
    // This scope must outlive ImageActionSheet. The sheet dismisses immediately
    // after Save, so a scope remembered inside the sheet would be cancelled
    // before the MediaStore write can finish.
    val imageSaveScope = rememberCoroutineScope()

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
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is EmailDetailUiState.BodyError -> {
                    EmailDetailBodyError(
                        state = state,
                        pdfDownloadStates = pdfDownloadStates,
                        onPdfAttachmentClick = onPdfAttachmentClick,
                        onPdfSaveClick = onPdfSaveClick,
                        savingStableIds = savingStableIds,
                        onRetry = onRetryBody,
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
                        onPdfAttachmentClick = onPdfAttachmentClick,
                        onPdfSaveClick = onPdfSaveClick,
                        savingStableIds = savingStableIds,
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
                    saveCoroutineScope = imageSaveScope,
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
