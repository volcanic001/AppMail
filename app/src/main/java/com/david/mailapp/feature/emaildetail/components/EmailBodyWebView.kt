package com.david.mailapp.feature.emaildetail.components

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
 * @param body Raw HTML email body (already decoded from Base64URL), or null
 * while the body is still being prepared. The WebView remains mounted in both states.
 * @param showImages When true, remote network loads are allowed.
 * @param isDark Whether the app is in dark mode — drives CSS injection.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmailBodyWebView(
    body: String?,
    showImages: Boolean = true,
    isDark: Boolean,
    traceMail: String,
    onPageRendered: (() -> Unit)? = null,
    onImageLongPress: ((imageUrl: String) -> Unit)? = null,
    modifier: Modifier = Modifier
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

    EmailBodyWebViewHost(
        context = context,
        currentKey = currentKey,
        preparedDocument = preparedDocument,
        surfaceArgb = surfaceArgb,
        showImages = showImages,
        isDark = isDark,
        runtimeState = runtimeState,
        traceMail = traceMail,
        onPageRendered = onPageRendered,
        onImageLongPress = onImageLongPress,
        modifier = modifier
    )
}
