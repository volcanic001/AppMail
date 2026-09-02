package com.david.mailapp.feature.emaildetail.components

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.lang.ref.WeakReference

internal class EmailBodyWebViewRuntimeState {
    val lastLoaded: MutableState<String?> = mutableStateOf(null)
    val activeLoadKey: MutableState<String?> = mutableStateOf(null)
    val loggedSkippedKey: MutableState<String?> = mutableStateOf(null)
    val loggedWaitingState: MutableState<String?> = mutableStateOf(null)
    val savedScrollY: MutableIntState = mutableIntStateOf(0)
    val webViewRef: MutableState<WeakReference<WebView>?> = mutableStateOf(null)
    val released: MutableState<Boolean> = mutableStateOf(false)
    val initialVisualReady: MutableState<Boolean> = mutableStateOf(false)
    val instanceGeneration: MutableIntState = mutableIntStateOf(0)
    val rendererReloadAttempts: MutableIntState = mutableIntStateOf(0)
    val rendererFailure: MutableState<RendererFailure?> = mutableStateOf(null)
    val recoveryInProgress: MutableState<Boolean> = mutableStateOf(false)
}

internal data class RendererFailure(
    val didCrash: Boolean,
    val canRetry: Boolean
)

internal fun EmailBodyWebViewRuntimeState.retryRenderer() {
    if (rendererFailure.value?.canRetry != true) return
    rendererReloadAttempts.intValue++
    rendererFailure.value = null
    recoveryInProgress.value = true
    released.value = false
    lastLoaded.value = null
    activeLoadKey.value = null
    loggedSkippedKey.value = null
    loggedWaitingState.value = null
    initialVisualReady.value = false
    instanceGeneration.intValue++
}

@Composable
internal fun rememberEmailBodyWebViewRuntimeState(): EmailBodyWebViewRuntimeState =
    remember { EmailBodyWebViewRuntimeState() }
