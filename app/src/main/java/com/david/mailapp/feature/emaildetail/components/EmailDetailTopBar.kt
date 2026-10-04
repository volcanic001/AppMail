package com.david.mailapp.feature.emaildetail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.david.mailapp.R
import com.david.mailapp.feature.emaildetail.EmailDetailUiState
import com.david.mailapp.feature.emaildetail.MaterialSymbolsReply
import com.david.mailapp.feature.emaildetail.TablerArrowForwardUpDouble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailDetailTopBar(
    uiState: EmailDetailUiState,
    onBack: () -> Unit,
    onReply: (String) -> Unit,
    onForward: (String) -> Unit,
    onToggleStar: () -> Unit
) {
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
            val currentEmail = when (uiState) {
                is EmailDetailUiState.Ready -> uiState.email
                is EmailDetailUiState.Empty -> uiState.email
                else -> null
            }
            val isStarred = currentEmail?.isStarred == true
            IconButton(
                onClick = onToggleStar,
                enabled = currentEmail != null
            ) {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (isStarred) R.string.detail_remove_favorite else R.string.detail_add_favorite
                    ),
                    tint = when {
                        currentEmail == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        isStarred -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
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
}
