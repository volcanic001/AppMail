package com.david.mailapp.feature.emaildetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.david.mailapp.BuildConfig
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import com.david.mailapp.ui.theme.LocalThemeConfig
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// EmailDetailContent
// Fills the full available space — no handle bar above it.
// Explicit routing for body:
// 1) body == null: Compose indicator without creating WebView.
// 2) bodyKind == PLAIN_TEXT: Native EmailPlainTextBody.
// 3) HTML / UNKNOWN: EmailBodyWebView.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailDetailContent(
    email: Email,
    body: String?,
    traceMail: String,
    pdfDownloadStates: Map<String, PdfDownloadState>,
    onPdfAttachmentClick: (PdfAttachmentMetadata) -> Unit,
    onPdfSaveClick: (PdfAttachmentMetadata) -> Unit,
    savingStableIds: Set<String>,
    onImageLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalThemeConfig.current.darkTheme
    val showImages = true
    val colorScheme = MaterialTheme.colorScheme
    val bodyKey = remember(
        body,
        isDark,
        showImages,
        colorScheme.surface,
        colorScheme.onSurface,
        colorScheme.primary
    ) {
        "${body?.hashCode()}_${isDark}_${showImages}_" +
            "${colorScheme.surface.hashCode()}_${colorScheme.onSurface.hashCode()}_" +
            colorScheme.primary.hashCode()
    }
    val baseContentKey = remember(email.id, email.bodyKind, email.cleanBody) {
        "${email.id}_${email.bodyKind}_${email.cleanBody.hashCode()}"
    }
    var isBaseContentRendered by remember(baseContentKey) { mutableStateOf(false) }

    val showLoader = body == null || (email.bodyKind != EmailBodyKind.PLAIN_TEXT && !isBaseContentRendered)
    val lastBodyLayout = remember { mutableStateOf<String?>(null) }

    DisposableEffect(traceMail, email.id) {
        EmailRenderTrace.d(traceMail, "UI", "UI_CONTENT_ENTER")
        onDispose {
            EmailRenderTrace.d(traceMail, "UI", "UI_CONTENT_DISPOSE")
        }
    }

    LaunchedEffect(bodyKey) {
        EmailRenderTrace.d(
            traceMail,
            "UI",
            "UI_BODY_INPUT",
            "present=${body != null} bodyLen=${body?.length ?: 0} bodyKey=$bodyKey"
        )
    }

    LaunchedEffect(showLoader, baseContentKey) {
        val reason = when {
            body == null -> "body_missing"
            email.bodyKind == EmailBodyKind.PLAIN_TEXT -> "plain_text_native"
            !isBaseContentRendered -> "awaiting_visual_callback"
            else -> "rendered"
        }
        EmailRenderTrace.d(
            traceMail,
            "UI",
            if (showLoader) "UI_LOADER_SHOWN" else "UI_LOADER_HIDDEN",
            "reason=$reason baseContentKey=$baseContentKey"
        )
        if (!showLoader) {
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.onVisualReady(traceMail)
        }
        withFrameNanos { frameTimeNanos ->
            EmailRenderTrace.d(
                traceMail,
                "UI",
                "UI_FRAME",
                "loaderVisible=$showLoader baseContentKey=$baseContentKey frameNanos=$frameTimeNanos"
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    val snapshot =
                        "x=${position.x.roundToInt()} y=${position.y.roundToInt()} " +
                            "width=${coordinates.size.width} height=${coordinates.size.height}"
                    if (lastBodyLayout.value != snapshot) {
                        lastBodyLayout.value = snapshot
                        EmailRenderTrace.d(traceMail, "UI", "UI_BODY_LAYOUT", snapshot)
                    }
                }
        ) {
            when {
                body == null -> {
                    // Route 1: body == null (PreparingBody) — Compose loading indicator without WebView
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                email.bodyKind == EmailBodyKind.PLAIN_TEXT -> {
                    // Route 2: READY + PLAIN_TEXT — Native Compose text body without WebView
                    EmailPlainTextBody(
                        text = body,
                        traceMail = traceMail,
                        onOpenLink = { url ->
                            SafeLinkPolicy.openSafeUrl(context, url)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    // Route 3: HTML / UNKNOWN — Existing EmailBodyWebView path
                    DisposableEffect(traceMail) {
                        EmailRenderTrace.d(traceMail, "UI", "UI_WEBVIEW_SLOT_ENTER", "bodyKey=$bodyKey")
                        onDispose {
                            EmailRenderTrace.d(traceMail, "UI", "UI_WEBVIEW_SLOT_DISPOSE", "bodyKey=$bodyKey")
                        }
                    }
                    EmailBodyWebView(
                        body = body,
                        showImages = showImages,
                        isDark = isDark,
                        traceMail = traceMail,
                        onPageRendered = {
                            if (!isBaseContentRendered) {
                                isBaseContentRendered = true
                                EmailRenderTrace.d(
                                    traceMail,
                                    "UI",
                                    "UI_INITIAL_TEXT_VISIBLE",
                                    "baseContentKey=$baseContentKey"
                                )
                                EmailRenderTrace.d(
                                    traceMail,
                                    "UI",
                                    "UI_RENDER_CALLBACK",
                                    "bodyKey=$bodyKey wasRendered=false"
                                )
                            } else {
                                EmailRenderTrace.d(
                                    traceMail,
                                    "UI",
                                    "UI_PROGRESSIVE_RELOAD",
                                    "baseContentKey=$baseContentKey bodyKey=$bodyKey"
                                )
                            }
                        },
                        onImageLongPress = onImageLongPress,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    )

                    if (showLoader) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .zIndex(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (BuildConfig.PERF_TRACE_ENABLED) {
                        Box(
                            modifier = Modifier
                                .size(1.dp)
                                .testTag("email_detail_visual_ready")
                        )
                    }
                }
            }
        }

        // PDF attachments section below the body
        PdfAttachmentSection(
            attachments = email.pdfAttachments,
            downloadStates = pdfDownloadStates,
            onAttachmentClick = onPdfAttachmentClick,
            onSaveClick = onPdfSaveClick,
            savingStableIds = savingStableIds
        )
    }
}
