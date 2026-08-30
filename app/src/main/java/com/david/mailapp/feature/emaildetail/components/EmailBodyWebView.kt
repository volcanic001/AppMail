package com.david.mailapp.feature.emaildetail.components

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.lang.ref.WeakReference

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
 * - D10: Lifecycle managed via [DisposableEffect] + [LifecycleEventObserver];
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = runtimeState.webViewRef.value?.get()
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (webView == null) {
                        EmailRenderTrace.d(traceMail, "WV", "WV_ON_PAUSE", "hasWebView=false")
                        return@LifecycleEventObserver
                    }
                    runtimeState.savedScrollY.intValue = webView.scrollY
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_ON_PAUSE",
                        "hasWebView=true scrollY=${webView.scrollY}"
                    )
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (webView == null) {
                        EmailRenderTrace.d(traceMail, "WV", "WV_ON_RESUME", "hasWebView=false")
                        return@LifecycleEventObserver
                    }
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_ON_RESUME",
                        "hasWebView=true savedScrollY=${runtimeState.savedScrollY.intValue}"
                    )
                    webView.onResume()
                    val resumeLoadKey = runtimeState.activeLoadKey.value
                    if (resumeLoadKey == null) {
                        EmailRenderTrace.d(
                            traceMail,
                            "WV",
                            "WV_RESUME_VISUAL_SKIPPED",
                            "reason=no_active_document"
                        )
                        return@LifecycleEventObserver
                    }
                    EmailRenderTrace.d(traceMail, "WV", "WV_RESUME_VISUAL_REQUESTED")
                    webView.postVisualStateCallback(
                        0L,
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_RESUME_VISUAL_CALLBACK",
                                    "loadKey=$resumeLoadKey activeLoadKey=${runtimeState.activeLoadKey.value} " +
                                        "released=${runtimeState.released.value} requestId=$requestId"
                                )
                                if (runtimeState.released.value || runtimeState.activeLoadKey.value != resumeLoadKey) return
                                webView.post {
                                    if (runtimeState.released.value || runtimeState.activeLoadKey.value != resumeLoadKey) {
                                        return@post
                                    }
                                    webView.scrollTo(0, runtimeState.savedScrollY.intValue)
                                    webView.invalidate()
                                    EmailRenderTrace.d(
                                        traceMail,
                                        "WV",
                                        "WV_RESUME_SCROLL_APPLIED",
                                        "scrollY=${runtimeState.savedScrollY.intValue}"
                                    )
                                }
                            }
                        }
                    )
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            EmailRenderTrace.d(traceMail, "WV", "WV_LIFECYCLE_OBSERVER_DISPOSE")
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    val instance = System.identityHashCode(this).toUInt().toString(16)
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_FACTORY",
                        "instance=$instance loadKey=${currentKey ?: "none"}"
                    )
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(surfaceArgb)
                    settings.applyHardening(showImages, isDark)

                    addOnAttachStateChangeListener(
                        object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) {
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_ATTACHED",
                                    "instance=$instance width=${view.width} height=${view.height}"
                                )
                            }

                            override fun onViewDetachedFromWindow(view: View) {
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_DETACHED",
                                    "instance=$instance width=${view.width} height=${view.height}"
                                )
                            }
                        }
                    )

                    // Detectar long-press sobre imágenes
                    setOnLongClickListener {
                        val hitTestResult = this.hitTestResult
                        if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                            hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                            val imageUrl = hitTestResult.extra
                            if (!imageUrl.isNullOrBlank()) {
                                onImageLongPress?.invoke(imageUrl)
                                return@setOnLongClickListener true
                            }
                        }
                        false
                    }
                }.also { runtimeState.webViewRef.value = WeakReference(it) }
            },
            update = { webView ->
                val document = preparedDocument
                if (document == null) {
                    val waitingState = if (currentKey == null) {
                        "body_pending"
                    } else {
                        "html_pending:$currentKey"
                    }
                    if (runtimeState.loggedWaitingState.value != waitingState) {
                        runtimeState.loggedWaitingState.value = waitingState
                        EmailRenderTrace.d(
                            traceMail,
                            "WV",
                            "WV_UPDATE",
                            "action=wait reason=$waitingState"
                        )
                    }
                } else if (runtimeState.lastLoaded.value != document.key) {
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_UPDATE",
                        "action=load previousKey=${runtimeState.lastLoaded.value ?: "none"} loadKey=${document.key} " +
                            "htmlLen=${document.html.length}"
                    )
                    runtimeState.lastLoaded.value = document.key
                    runtimeState.activeLoadKey.value = document.key
                    runtimeState.loggedSkippedKey.value = null
                    runtimeState.loggedWaitingState.value = null
                    runtimeState.released.value = false
                    runtimeState.webViewRef.value = WeakReference(webView)
                    webView.setBackgroundColor(surfaceArgb)
                    webView.settings.applyHardening(showImages, isDark)
                    webView.webChromeClient = TraceWebChromeClient(traceMail, document.key)
                    webView.webViewClient = CustomTabsWebViewClient(
                        context,
                        traceMail,
                        document.key
                    ) {
                        if (!runtimeState.released.value && runtimeState.activeLoadKey.value == document.key) {
                            EmailRenderTrace.d(
                                traceMail,
                                "WV",
                                "WV_SCROLL_RESTORE_POSTED",
                                "loadKey=${document.key} scrollY=${runtimeState.savedScrollY.intValue}"
                            )
                            webView.post {
                                if (runtimeState.released.value || runtimeState.activeLoadKey.value != document.key) {
                                    EmailRenderTrace.d(
                                        traceMail,
                                        "WV",
                                        "WV_PAGE_RENDERED_IGNORED",
                                        "loadKey=${document.key} reason=stale_after_post"
                                    )
                                    return@post
                                }
                                webView.scrollTo(0, runtimeState.savedScrollY.intValue)
                                webView.invalidate()
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_SCROLL_RESTORE_APPLIED",
                                    "loadKey=${document.key} scrollY=${runtimeState.savedScrollY.intValue}"
                                )
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_PAGE_RENDERED_DISPATCH",
                                    "loadKey=${document.key}"
                                )
                                onPageRendered?.invoke()
                            }
                        } else {
                            EmailRenderTrace.d(
                                traceMail,
                                "WV",
                                "WV_PAGE_RENDERED_IGNORED",
                                "loadKey=${document.key} activeLoadKey=${runtimeState.activeLoadKey.value} " +
                                    "released=${runtimeState.released.value} reason=stale_or_released"
                            )
                        }
                    }
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_LOAD_DATA",
                        "loadKey=${document.key} htmlLen=${document.html.length}"
                    )
                    webView.loadDataWithBaseURL(
                        null,
                        document.html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else if (runtimeState.loggedSkippedKey.value != document.key) {
                    runtimeState.loggedSkippedKey.value = document.key
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_UPDATE",
                        "action=skip loadKey=${document.key} reason=already_loaded"
                    )
                }
            },
            onRelease = { webView ->
                EmailRenderTrace.d(
                    traceMail,
                    "WV",
                    "WV_RELEASE",
                    "loadKey=${runtimeState.activeLoadKey.value ?: "none"} scrollY=${webView.scrollY}"
                )
                runtimeState.savedScrollY.intValue = webView.scrollY
                runtimeState.released.value = true
                runtimeState.activeLoadKey.value = null
                runtimeState.webViewRef.value = null
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
