package com.david.mailapp.feature.emaildetail.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.david.mailapp.R
import com.david.mailapp.core.webview.ProcessWebViewStartupGate
import com.david.mailapp.core.webview.WebViewStartupGate
import com.david.mailapp.core.webview.WebViewStartupState
import com.david.mailapp.feature.emaildetail.EmailRenderTrace

/**
 * Renders email HTML body in a hardened WebView.
 *
 * Design decisions per the unified body-rendering spec:
 * - D2: [loadDataWithBaseURL] with null baseUrl — no relative resource resolution.
 * - D3: `WebSettings.blockNetworkLoads` = !showImages (kills all remote trackers).
 * - D4: `WebSettings.javaScriptEnabled` = false — always.
 * - D6: CSS-injected dark mode from [MaterialTheme.colorScheme] colors
 *   + `WebSettingsCompat.setAlgorithmicDarkeningAllowed` for content without
 *   its own background.
 * - D7: `WebViewClient.shouldOverrideUrlLoading` → Chrome Custom Tabs.
 * - D10: Lifecycle managed via [DisposableEffect] + `LifecycleEventObserver`;
 *   scroll position is preserved across ON_PAUSE/ON_RESUME and restored
 *   after new document loads via [postVisualStateCallback].
 *
 * @param body Prepared HTML email body. The host is mounted only after asynchronous
 * WebView startup has completed.
 * @param showImages When true, remote network loads are allowed.
 * @param isDark Whether the app is in dark mode — drives CSS injection.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EmailBodyWebView(
    body: String?,
    modifier: Modifier = Modifier,
    showImages: Boolean = true,
    isDark: Boolean,
    traceMail: String,
    onPageRendered: (() -> Unit)? = null,
    onRenderUnavailable: ((reason: String) -> Unit)? = null,
    onImageLongPress: ((imageUrl: String) -> Unit)? = null,
    startupGate: WebViewStartupGate = ProcessWebViewStartupGate
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scheme = MaterialTheme.colorScheme
    val surfaceArgb = scheme.surface.toArgb()
    val onSurfaceArgb = scheme.onSurface.toArgb()
    val primaryArgb = scheme.primary.toArgb()

    val currentKey = remember(
        body,
        showImages,
        isDark,
        surfaceArgb,
        onSurfaceArgb,
        primaryArgb
    ) {
        body?.let {
            buildLoadKey(
                it,
                showImages,
                isDark,
                surfaceArgb,
                onSurfaceArgb,
                primaryArgb
            )
        }
    }

    val preparedDocument = rememberPreparedEmailBodyDocument(
        body = body,
        currentKey = currentKey,
        showImages = showImages,
        isDark = isDark,
        surfaceArgb = surfaceArgb,
        onSurfaceArgb = onSurfaceArgb,
        primaryArgb = primaryArgb,
        traceMail = traceMail
    )

    val runtimeState = rememberEmailBodyWebViewRuntimeState()
    val startupState by startupGate.state.collectAsState()

    LaunchedEffect(startupGate) {
        startupGate.start(context)
    }

    LaunchedEffect(startupState) {
        if (startupState is WebViewStartupState.Failed) {
            onRenderUnavailable?.invoke("webview_startup_failed")
        }
    }

    DisposableEffect(traceMail) {
        EmailRenderTrace.d(
            traceMail,
            "WV",
            "WV_COMPOSABLE_ENTER",
            "loadKey=${currentKey ?: "none"} bodyLen=${body?.length ?: 0}"
        )
        onDispose {
            EmailRenderTrace.d(
                traceMail,
                "WV",
                "WV_COMPOSABLE_DISPOSE",
                "loadKey=${currentKey ?: "none"}"
            )
        }
    }

    BindEmailBodyWebViewLifecycle(
        lifecycleOwner = lifecycleOwner,
        runtimeState = runtimeState,
        traceMail = traceMail
    )

    when (startupState) {
        WebViewStartupState.Idle, WebViewStartupState.Starting -> {
            WebViewBodyLoading(modifier)
        }
        WebViewStartupState.Ready -> {
            val rendererFailure = runtimeState.rendererFailure.value
            if (rendererFailure != null) {
                WebViewBodyFailure(
                    repeated = !rendererFailure.canRetry,
                    onReload = if (rendererFailure.canRetry) {
                        { runtimeState.retryRenderer() }
                    } else null,
                    modifier = modifier
                )
            } else {
                Box(modifier = modifier) {
                    EmailBodyWebViewHost(
                        context = context,
                        currentKey = currentKey,
                        preparedDocument = preparedDocument,
                        surfaceArgb = surfaceArgb,
                        showImages = showImages,
                        isDark = isDark,
                        runtimeState = runtimeState,
                        traceMail = traceMail,
                        onPageRendered = {
                            runtimeState.recoveryInProgress.value = false
                            onPageRendered?.invoke()
                        },
                        onRendererGone = {
                            onRenderUnavailable?.invoke("renderer_gone")
                        },
                        onImageLongPress = onImageLongPress,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (runtimeState.recoveryInProgress.value) {
                        WebViewBodyLoading(Modifier.fillMaxSize())
                    }
                }
            }
        }
        is WebViewStartupState.Failed -> {
            WebViewBodyFailure(
                repeated = false,
                onReload = { startupGate.retry(context) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun WebViewBodyLoading(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun WebViewBodyFailure(
    repeated: Boolean,
    onReload: (() -> Unit)?,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(
                if (repeated) R.string.detail_webview_repeated_error
                else R.string.detail_webview_render_error
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onReload != null) {
            TextButton(onClick = onReload) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_reload))
            }
        }
    }
}
