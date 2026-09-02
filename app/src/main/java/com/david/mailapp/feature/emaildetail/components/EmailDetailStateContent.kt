package com.david.mailapp.feature.emaildetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.EmailDetailUiState

@Composable
internal fun EmailDetailLoading(modifier: Modifier = Modifier) {
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
internal fun EmailDetailResolutionError(
    state: EmailDetailUiState.ResolutionError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
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
                onClick = { onRetry() }
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
internal fun EmailDetailBodyError(
    state: EmailDetailUiState.BodyError,
    pdfDownloadStates: Map<String, PdfDownloadState>,
    onPdfAttachmentClick: (PdfAttachmentMetadata) -> Unit,
    onPdfSaveClick: (PdfAttachmentMetadata) -> Unit,
    savingStableIds: Set<String>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pdfEmail = state.email
    Column(
        modifier = modifier.fillMaxSize()
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
                        onClick = { onRetry() }
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
                onAttachmentClick = onPdfAttachmentClick,
                onSaveClick = onPdfSaveClick,
                savingStableIds = savingStableIds
            )
        }
    }
}

@Composable
internal fun EmailDetailEmpty(
    state: EmailDetailUiState.Empty,
    pdfDownloadStates: Map<String, PdfDownloadState>,
    onPdfAttachmentClick: (PdfAttachmentMetadata) -> Unit,
    onPdfSaveClick: (PdfAttachmentMetadata) -> Unit,
    savingStableIds: Set<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        if (state.email.pdfAttachments.isEmpty()) {
                            R.string.detail_body_empty
                        } else {
                            R.string.detail_body_empty_with_pdfs
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.email.pdfAttachments.isNotEmpty()) {
            PdfAttachmentSection(
                attachments = state.email.pdfAttachments,
                downloadStates = pdfDownloadStates,
                onAttachmentClick = onPdfAttachmentClick,
                onSaveClick = onPdfSaveClick,
                savingStableIds = savingStableIds
            )
        }
    }
}
