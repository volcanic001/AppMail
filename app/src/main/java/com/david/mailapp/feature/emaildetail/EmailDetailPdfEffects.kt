package com.david.mailapp.feature.emaildetail

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "EmailDetailScreen"

@Composable
internal fun rememberEmailDetailPdfEffects(
    viewModel: EmailDetailViewModel,
    lifecycleOwner: LifecycleOwner,
    snackbarHostState: androidx.compose.material3.SnackbarHostState
): State<Set<String>> {
    val screenContext = LocalContext.current
    val scope = rememberCoroutineScope()

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
            val repository = AppContainer.emailRepository
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

    return savingState
}
