package com.david.mailapp.feature.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.feature.compose.ComposeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeTopBar(
    mode: ComposeMode,
    isSending: Boolean,
    onClose: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes = when (mode) {
        ComposeMode.WRITE -> R.string.compose_title_write
        ComposeMode.REPLY -> R.string.compose_title_reply
        ComposeMode.FORWARD -> R.string.compose_title_forward
    }

    TopAppBar(
        title = {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        },
        actions = {
            if (isSending) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.7.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}
