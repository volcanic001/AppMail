package com.david.mailapp.feature.emaildetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.SaveAlt
import com.david.mailapp.R
import com.david.mailapp.core.localization.UiText
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiErrorReason
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.data.pdf.PdfDownloadState
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// PdfAttachmentSection — compact fixed section below the HTML body
// Shows at most 2 PDFs; "Mostrar todos (N)" opens a ModalBottomSheet.
// Each card reflects the attachment's download state.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfAttachmentSection(
    attachments: List<PdfAttachmentMetadata>,
    downloadStates: Map<String, PdfDownloadState>,
    onAttachmentClick: (PdfAttachmentMetadata) -> Unit,
    onSaveClick: (PdfAttachmentMetadata) -> Unit,
    savingStableIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) {
        return
    }

    var showAllAttachments by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Close the bottom sheet automatically if the list shrinks to ≤ 2
    LaunchedEffect(attachments.size) {
        if (attachments.size <= 2 && showAllAttachments) {
            showAllAttachments = false
        }
    }

    // Bottom sheet
    if (showAllAttachments) {
        ModalBottomSheet(
            onDismissRequest = { showAllAttachments = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.pdf_attachments_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                // Full list — scrollable via LazyColumn
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = attachments,
                        key = { it.stableId }
                    ) { attachment ->
                        PdfAttachmentItem(
                            attachment = attachment,
                            state = downloadStates[attachment.stableId] ?: PdfDownloadState.Idle,
                            isSaving = attachment.stableId in savingStableIds,
                            onClick = { onAttachmentClick(attachment) },
                            onSaveClick = { onSaveClick(attachment) }
                        )
                    }
                    // Bottom spacing for navigation bar clearance
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    // Compact section (always visible, at most 2 items)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header
                Text(
                    text = pluralStringResource(
                        R.plurals.pdf_attachments_count,
                        attachments.size,
                        attachments.size
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                // At most 2 PDF cards
                attachments.take(2).forEach { attachment ->
                    PdfAttachmentItem(
                        attachment = attachment,
                        state = downloadStates[attachment.stableId] ?: PdfDownloadState.Idle,
                        isSaving = attachment.stableId in savingStableIds,
                        onClick = { onAttachmentClick(attachment) },
                        onSaveClick = { onSaveClick(attachment) },
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // "Mostrar todos" button when there are more than 2
                if (attachments.size > 2) {
                    TextButton(
                        onClick = { showAllAttachments = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.pdf_attachments_show_all,
                                attachments.size
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PdfAttachmentItem — state-aware card showing PDF download status
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PdfAttachmentItem(
    attachment: PdfAttachmentMetadata,
    state: PdfDownloadState,
    isSaving: Boolean,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = state !is PdfDownloadState.Downloading) {
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon (varies by state)
            LeadingIcon(state = state, attachment = attachment)

            Spacer(Modifier.width(12.dp))

            // Title + status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))

                // Status line (varies by state)
                StatusLine(
                    state = state,
                    metadataSizeBytes = attachment.sizeBytes
                )
            }

            // Save button
            when {
                isSaving -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                state is PdfDownloadState.Downloading -> {
                    // No save button during download
                }
                else -> {
                    IconButton(
                        onClick = onSaveClick,
                        enabled = state !is PdfDownloadState.Downloading && !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SaveAlt,
                            contentDescription = stringResource(R.string.pdf_save_as),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LeadingIcon — different icon per state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LeadingIcon(
    state: PdfDownloadState,
    attachment: PdfAttachmentMetadata
) {
    when (state) {
        is PdfDownloadState.Idle -> {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = stringResource(R.string.pdf_attachment_icon_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        is PdfDownloadState.Downloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        is PdfDownloadState.Ready -> {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.pdf_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        is PdfDownloadState.Error -> {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = stringResource(R.string.pdf_error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusLine — secondary text per state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusLine(
    state: PdfDownloadState,
    metadataSizeBytes: Long?
) {
    val pdfLocale = LocalLocale.current.platformLocale
    when (state) {
        is PdfDownloadState.Idle -> {
            val sizeText = formatPdfAttachmentSize(metadataSizeBytes, pdfLocale)
                ?.asString()
                ?: stringResource(R.string.pdf_attachment_unknown_size)
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is PdfDownloadState.Downloading -> {
            Text(
                text = stringResource(R.string.pdf_downloading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is PdfDownloadState.Ready -> {
            Text(
                text = stringResource(R.string.pdf_ready),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        is PdfDownloadState.Error -> {
            val message = stringResource(
                state.reason.toUiErrorReason().toUiText().resId
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// formatPdfAttachmentSize — returns UiText.Resource, formatted with default locale
// ─────────────────────────────────────────────────────────────────────────────

internal fun formatPdfAttachmentSize(sizeBytes: Long?, locale: Locale = Locale.getDefault()): UiText.Resource? {
    if (sizeBytes == null || sizeBytes < 0) return null
    return when {
        sizeBytes < 1024 -> UiText.Resource(
            R.string.size_bytes,
            listOf(sizeBytes)
        )
        sizeBytes < 1024 * 1024 -> formatKb(sizeBytes, locale)
        sizeBytes < 1024L * 1024 * 1024 -> formatMb(sizeBytes, locale)
        else -> formatGb(sizeBytes, locale)
    }
}

private fun formatKb(bytes: Long, locale: Locale): UiText.Resource {
    val kb = bytes / 1024.0
    return UiText.Resource(
        R.string.size_kb,
        listOf(formatSizeDecimal(kb, locale))
    )
}

private fun formatMb(bytes: Long, locale: Locale): UiText.Resource {
    val mb = bytes / (1024.0 * 1024.0)
    return UiText.Resource(
        R.string.size_mb,
        listOf(formatSizeDecimal(mb, locale))
    )
}

private fun formatGb(bytes: Long, locale: Locale): UiText.Resource {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return UiText.Resource(
        R.string.size_gb,
        listOf(formatSizeDecimal(gb, locale))
    )
}

/** Trunca a un decimal usando el locale dado. */
internal fun formatSizeDecimal(value: Double, locale: Locale): String {
    val rounded = (value * 10).toLong() / 10.0
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) {
        asLong.toString()
    } else {
        val formatted = "%.1f".format(locale, rounded)
        if (formatted.endsWith(".0")) formatted.dropLast(2) else formatted
    }
}
