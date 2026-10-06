package com.david.mailapp.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.david.mailapp.R
import com.david.mailapp.core.localization.asString
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.core.localization.UiErrorReason

@Composable
internal fun EmptyInbox() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("inbox_empty_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Text(stringResource(R.string.inbox_empty_symbol), fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Text(
            stringResource(R.string.inbox_empty),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Internal extraction for the existing recoverable error presentation. */
@Composable
internal fun InboxErrorContent(
    reason: UiErrorReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.error_symbol), fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = reason.toUiText().asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
