package com.david.mailapp.feature.emaildetail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.FileProvider

// ── PDF open request handling ─────────────────────────────────

/**
 * Resuelve el archivo cacheado desde [request], genera un URI con FileProvider
 * y lanza ACTION_VIEW (Open). Muestra Snackbar en caso de error.
 */
internal suspend fun handlePdfExternalActionRequest(
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
