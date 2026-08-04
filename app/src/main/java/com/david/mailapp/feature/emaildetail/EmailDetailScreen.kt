package com.david.mailapp.feature.emaildetail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.components.EmailBodyWebView
import com.david.mailapp.feature.emaildetail.components.ImageSaveLabels
import com.david.mailapp.feature.emaildetail.components.ImageUtils
import com.david.mailapp.feature.emaildetail.components.PdfAttachmentSection
import com.david.mailapp.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.app.Activity
import android.content.Intent
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
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
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                },
                actions = {
                    // Responder/Reenviar solo disponibles en Ready — durante
                    // resolución, preparación o error permanecen deshabilitados.
                    val currentEmail = (uiState as? EmailDetailUiState.Ready)?.email
                    IconButton(
                        onClick = {
                            currentEmail?.let { onReply(it.id) }
                        },
                        enabled = currentEmail != null
                    ) {
                        Icon(
                            MaterialSymbolsReply,
                            contentDescription = stringResource(R.string.detail_reply),
                            tint = if (currentEmail != null) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = {
                            currentEmail?.let { onForward(it.id) }
                        },
                        enabled = currentEmail != null
                    ) {
                        Icon(
                            TablerArrowForwardUpDouble,
                            contentDescription = stringResource(R.string.detail_forward),
                            tint = if (currentEmail != null) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.reason.toUiText().asString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.retryable) {
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.onRetry() }
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                is EmailDetailUiState.BodyError -> {
                    val pdfEmail = state.email
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = state.reason.toUiText().asString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (state.retryable) {
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.material3.TextButton(
                                        onClick = { viewModel.onRetryBody() }
                                    ) {
                                        Text(stringResource(R.string.action_retry))
                                    }
                                }
                            }
                        }
                        if (pdfEmail?.pdfAttachments?.isNotEmpty() == true) {
                            PdfAttachmentSection(
                                attachments = pdfEmail.pdfAttachments,
                                downloadStates = pdfDownloadStates,
                                onAttachmentClick = viewModel::onPdfAttachmentClick,
                                onSaveClick = viewModel::onPdfSaveClick,
                                savingStableIds = savingState.value
                            )
                        }
                    }
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
            if (activeImageUrl != null) {
                val saveLabels = ImageSaveLabels(
                    invalidFormatMessage = stringResource(R.string.image_invalid_format),
                    savedToGalleryMessage = stringResource(R.string.image_saved_to_gallery),
                    saveErrorMessage = stringResource(R.string.image_save_error),
                    filenameTemplate = stringResource(R.string.image_filename_format)
                )
                ModalBottomSheet(
                    onDismissRequest = { activeImageUrl = null },
                    sheetState = rememberModalBottomSheetState(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 12.dp) // Tightens top/bottom spacing
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.image_open)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // Eliminates background shadow box
                            modifier = Modifier.clickable {
                                showFullscreenImage = activeImageUrl
                                activeImageUrl = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.image_save)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // Eliminates background shadow box
                            modifier = Modifier.clickable {
                                val urlToSave = activeImageUrl
                                val resolvedLabels = saveLabels
                                if (urlToSave != null) {
                                    coroutineScope.launch {
                                        ImageUtils.saveImageToGallery(context, urlToSave, resolvedLabels)
                                    }
                                }
                                activeImageUrl = null
                            }
                        )
                    }
                }
            }
        }

        // ── Fullscreen image dialog (outside content Box) ──────
        if (showFullscreenImage != null) {
            val bitmap = remember(showFullscreenImage) {
                showFullscreenImage?.let { ImageUtils.decodeDataUriToBitmap(it) }
            }

            Dialog(
                onDismissRequest = { showFullscreenImage = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showFullscreenImage = null },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.image_fullscreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.image_load_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FloatingHeaderPanel
// An overlay panel anchored to the top of the screen.
// Collapsed: shows only a pill handle with a rotating arrow.
// Expanded: shows a Card with email metadata fields.
// Never pushes the WebView — it lives in a Box overlay at zIndex(2).
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FloatingHeaderPanel(
    email: Email,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    traceMail: String,
    modifier: Modifier = Modifier
) {
    val lastHeaderLayout = remember { mutableStateOf<String?>(null) }

    // Arrow rotation: 0° = collapsed (points up = "tap to expand"),
    // 180° = expanded (points down = "tap to collapse").
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "header_arrow_rotation"
    )

    // Offset the pill handle dynamically to ensure it is centered on the border when expanded
    // and correctly aligned below the TopAppBar when collapsed (preventing cut-off).
    val handleOffsetY by animateDpAsState(
        targetValue = if (isExpanded) (-20).dp else (-15).dp,
        animationSpec = tween(durationMillis = 250),
        label = "handle_offset_y"
    )

    val dateFormat = rememberDateFormat()
    val panelShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = (-5).dp) // Tucks the panel slightly under the TopAppBar to eliminate any gap
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val snapshot =
                    "x=${position.x.roundToInt()} y=${position.y.roundToInt()} " +
                        "width=${coordinates.size.width} height=${coordinates.size.height} " +
                        "expanded=$isExpanded"
                if (lastHeaderLayout.value != snapshot) {
                    lastHeaderLayout.value = snapshot
                    EmailRenderTrace.d(traceMail, "UI", "HEADER_LAYOUT", snapshot)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Expandable panel ───────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 250),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 220),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = panelShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Subject — prominent
                    Text(
                        text = email.subject.ifBlank { stringResource(R.string.detail_subject_missing) },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))

                    // Metadata rows
                    HeaderDetailRow(
                        icon = Icons.Outlined.Mail,
                        label = stringResource(R.string.detail_field_from_label),
                        value = email.from
                    )
                    if (email.to.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        HeaderDetailRow(
                            icon = Icons.Outlined.Person,
                            label = stringResource(R.string.detail_field_to_label),
                            value = email.to
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HeaderDetailRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.detail_field_date_label),
                        value = dateFormat.format(Date(email.timestamp))
                    )
                }
            }
        }

        // ── Tab handle (always visible, centered, attached to bottom) ─────────────
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier
                .zIndex(1f)
                .width(60.dp)
                .height(40.dp)
                .offset(y = handleOffsetY) // Dynamic offset to align handle perfectly
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 2.dp) // Centers the 16.dp icon perfectly within the visible bottom 20.dp semi-circle
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (isExpanded) stringResource(R.string.detail_collapse_header) else stringResource(R.string.detail_expand_header),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HeaderDetailRow
// A label + value row used inside FloatingHeaderPanel.
// Label has a fixed minimum width so all values align cleanly.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeaderDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circle container for icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmailDetailContent
// WebView fills the full available space — no handle bar above it.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailDetailContent(
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
    var isBodyRendered by remember(bodyKey) { mutableStateOf(false) }

    val showLoader = body == null || !isBodyRendered
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
        EmailRenderTrace.d(traceMail, "UI", "UI_RENDER_STATE_RESET", "bodyKey=$bodyKey")
    }

    LaunchedEffect(showLoader, bodyKey) {
        val reason = when {
            body == null -> "body_missing"
            !isBodyRendered -> "awaiting_visual_callback"
            else -> "rendered"
        }
        EmailRenderTrace.d(
            traceMail,
            "UI",
            if (showLoader) "UI_LOADER_SHOWN" else "UI_LOADER_HIDDEN",
            "reason=$reason bodyKey=$bodyKey"
        )
        withFrameNanos { frameTimeNanos ->
            EmailRenderTrace.d(
                traceMail,
                "UI",
                "UI_FRAME",
                "loaderVisible=$showLoader bodyKey=$bodyKey frameNanos=$frameTimeNanos"
            )
        }
    }

    // WebView fills the available vertical space, with PDF section below
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
                EmailRenderTrace.d(
                    traceMail,
                    "UI",
                    "UI_RENDER_CALLBACK",
                    "bodyKey=$bodyKey wasRendered=$isBodyRendered"
                )
                isBodyRendered = true
            },
            onImageLongPress = onImageLongPress,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        )
        // Loader overlay on top with solid surface background —
        // hides intermediate WebView frames until postVisualStateCallback.
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmailDetailLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat {
    val pattern = stringResource(R.string.date_pattern_long)
    return remember(pattern) {
        SimpleDateFormat(pattern, Locale.getDefault())
    }
}

// ── PDF open request handling ──────────────────────────────────

/**
 * Resuelve el archivo cacheado desde [request], genera un URI con FileProvider
 * y lanza ACTION_VIEW (Open). Muestra Snackbar en caso de error.
 */
private suspend fun handlePdfExternalActionRequest(
    context: android.content.Context,
    request: PdfExternalActionRequest,
    viewModel: EmailDetailViewModel,
    snackbarHostState: SnackbarHostState,
    labels: PdfActionLabels,
    defaultPdfFilename: String
) {
    val repository = com.david.mailapp.core.di.AppContainer.emailRepository
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        repository.getValidatedCachedPdf(request.emailId, request.stablePartId)
    }

    if (file == null) {
        viewModel.onPdfCacheExpired(request.stablePartId)
        snackbarHostState.showSnackbar(
            message = labels.cacheExpired
        )
        return
    }

    val displayName = sanitizeDisplayName(request.displayName, defaultPdfFilename)

    openPdfIntent(
        context, file, displayName, snackbarHostState, labels
    )
}

private suspend fun openPdfIntent(
    context: android.content.Context,
    file: java.io.File,
    displayName: String,
    snackbarHostState: SnackbarHostState,
    labels: PdfActionLabels
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("", uri)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        snackbarHostState.showSnackbar(
            message = labels.noViewer
        )
    } catch (e: IllegalArgumentException) {
        snackbarHostState.showSnackbar(
            message = labels.openFailed
        )
    } catch (e: SecurityException) {
        snackbarHostState.showSnackbar(
            message = labels.openFailed
        )
    }
}

/**
 * Sanitiza el nombre visible para el visor externo:
 * elimina separadores de ruta, caracteres de control y espacios extremos.
 * Si queda vacío, usa [defaultName].
 */
internal fun sanitizeDisplayName(name: String, defaultName: String): String {
    val sanitized = name
        .replace("/", "_")
        .replace("\\", "_")
        .replace(Regex("[\\x00-\\x1f\\x7f]"), "")
        .trim()
    return sanitized.ifBlank { defaultName }
}

/**
 * Construye el nombre sugerido para el selector SAF.
 * Sanitiza [displayName] y garantiza que termine en `.pdf`.
 */
internal fun buildPdfSuggestedName(displayName: String, defaultName: String): String {
    val sanitized = sanitizeDisplayName(displayName, defaultName)
    return if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
}

/**
 * Copia [source] al [destinationUri] usando ContentResolver con streams.
 * No carga el archivo completo en memoria — copia por bloques de 8 KiB.
 * Devuelve true si todos los bytes se copiaron correctamente.
 */
internal fun copyFileToUri(
    context: android.content.Context,
    source: java.io.File,
    destinationUri: android.net.Uri
): Boolean {
    return try {
        context.contentResolver.openOutputStream(destinationUri, "wt").use { output ->
            copyFileToStream(source, output)
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Copia [source] a [output] y verifica que se hayan escrito todos sus bytes.
 * Un proveedor SAF puede rechazar el URI devolviendo un stream nulo.
 */
internal fun copyFileToStream(
    source: java.io.File,
    output: java.io.OutputStream?
): Boolean {
    if (output == null) return false

    val sourceSize = source.length()
    return try {
        source.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var totalWritten = 0L
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
            }
            totalWritten == sourceSize
        }
    } catch (_: Exception) {
        false
    }
}

// ── Custom vector icons (MaterialSymbolsReply and FluentuiSystemIconsArrowForward) ──────

val MaterialSymbolsReply: ImageVector
    get() {
        if (_MaterialSymbolsReply != null) return _MaterialSymbolsReply!!
        _MaterialSymbolsReply = ImageVector.Builder(
            name = "reply",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(760f, 760f)
                verticalLineToRelative(-160f)
                quadToRelative(0f, -50f, -35f, -85f)
                reflectiveQuadToRelative(-85f, -35f)
                horizontalLineTo(273f)
                lineToRelative(144f, 144f)
                lineToRelative(-57f, 56f)
                lineToRelative(-240f, -240f)
                lineToRelative(240f, -240f)
                lineToRelative(57f, 56f)
                lineToRelative(-144f, 144f)
                horizontalLineToRelative(367f)
                quadToRelative(83f, 0f, 141.5f, 58.5f)
                reflectiveQuadTo(840f, 600f)
                verticalLineToRelative(160f)
                horizontalLineToRelative(-80f)
                close()
            }
        }.build()
        return _MaterialSymbolsReply!!
    }

private var _MaterialSymbolsReply: ImageVector? = null

val TablerArrowForwardUpDouble: ImageVector
    get() {
        if (_TablerArrowForwardUpDouble != null) return _TablerArrowForwardUpDouble!!
        _TablerArrowForwardUpDouble = ImageVector.Builder(
            name = "arrow-forward-up-double",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11f, 14f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 14f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 10f)
                horizontalLineToRelative(-7f)
                arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
                horizontalLineToRelative(1f)
            }
        }.build()
        return _TablerArrowForwardUpDouble!!
    }

private var _TablerArrowForwardUpDouble: ImageVector? = null

// ── PdfActionLabels — etiquetas resueltas para callbacks no composables ──

internal data class PdfActionLabels(
    val cacheExpired: String,
    val saved: String,
    val saveFailed: String,
    val noFilePicker: String,
    val pickerOpenFailed: String,
    val noViewer: String,
    val openFailed: String
)
