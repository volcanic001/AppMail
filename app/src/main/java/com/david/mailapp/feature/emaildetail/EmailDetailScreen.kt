package com.david.mailapp.feature.emaildetail

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    onBack: () -> Unit,
    onReply: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    EmailDetailRoute(
        emailId = emailId,
        onBack = onBack,
        onReply = onReply,
        onForward = onForward,
        modifier = modifier
    )
}
