package com.david.mailapp.feature.emaildetail

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.resolve
import com.david.mailapp.core.localization.toUiText
import kotlinx.coroutines.flow.Flow

@Composable
internal fun EmailDetailReadFailureEffect(
    failureEvents: Flow<UiErrorReason>,
    snackbarHostState: SnackbarHostState,
    stringProvider: StringProvider
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, failureEvents, snackbarHostState, stringProvider) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            failureEvents.collect { reason ->
                snackbarHostState.showSnackbar(
                    message = reason.toUiText().resolve(stringProvider),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
}
