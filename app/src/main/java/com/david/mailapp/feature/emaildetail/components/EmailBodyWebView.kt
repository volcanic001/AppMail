package com.david.mailapp.feature.emaildetail.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * - D7: [WebViewClient.shouldOverrideUrlLoading] → Chrome Custom Tabs.
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

    // Track the document actually loaded into this WebView. This is separate
    // from the body being prepared so stale visual callbacks can be rejected.
    var lastLoaded by remember { mutableStateOf<String?>(null) }
    var activeLoadKey by remember { mutableStateOf<String?>(null) }
    var loggedSkippedKey by remember { mutableStateOf<String?>(null) }
    var loggedWaitingState by remember { mutableStateOf<String?>(null) }

    // ── Lifecycle-aware scroll preservation ────────────────────
    val savedScrollY = remember { mutableIntStateOf(0) }
    val webViewRef = remember { mutableStateOf<WeakReference<WebView>?>(null) }
    val released = remember { mutableStateOf(false) }

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
            val webView = webViewRef.value?.get()
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (webView == null) {
                        EmailRenderTrace.d(traceMail, "WV", "WV_ON_PAUSE", "hasWebView=false")
                        return@LifecycleEventObserver
                    }
                    savedScrollY.intValue = webView.scrollY
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
                        "hasWebView=true savedScrollY=${savedScrollY.intValue}"
                    )
                    webView.onResume()
                    val resumeLoadKey = activeLoadKey
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
                                    "loadKey=$resumeLoadKey activeLoadKey=$activeLoadKey " +
                                        "released=${released.value} requestId=$requestId"
                                )
                                if (released.value || activeLoadKey != resumeLoadKey) return
                                webView.post {
                                    if (released.value || activeLoadKey != resumeLoadKey) {
                                        return@post
                                    }
                                    webView.scrollTo(0, savedScrollY.intValue)
                                    webView.invalidate()
                                    EmailRenderTrace.d(
                                        traceMail,
                                        "WV",
                                        "WV_RESUME_SCROLL_APPLIED",
                                        "scrollY=${savedScrollY.intValue}"
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
                }.also { webViewRef.value = WeakReference(it) }
            },
            update = { webView ->
                val document = preparedDocument
                if (document == null) {
                    val waitingState = if (currentKey == null) {
                        "body_pending"
                    } else {
                        "html_pending:$currentKey"
                    }
                    if (loggedWaitingState != waitingState) {
                        loggedWaitingState = waitingState
                        EmailRenderTrace.d(
                            traceMail,
                            "WV",
                            "WV_UPDATE",
                            "action=wait reason=$waitingState"
                        )
                    }
                } else if (lastLoaded != document.key) {
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_UPDATE",
                        "action=load previousKey=${lastLoaded ?: "none"} loadKey=${document.key} " +
                            "htmlLen=${document.html.length}"
                    )
                    lastLoaded = document.key
                    activeLoadKey = document.key
                    loggedSkippedKey = null
                    loggedWaitingState = null
                    released.value = false
                    webViewRef.value = WeakReference(webView)
                    webView.setBackgroundColor(surfaceArgb)
                    webView.settings.applyHardening(showImages, isDark)
                    webView.webChromeClient = TraceWebChromeClient(traceMail, document.key)
                    webView.webViewClient = CustomTabsWebViewClient(
                        context,
                        traceMail,
                        document.key
                    ) {
                        if (!released.value && activeLoadKey == document.key) {
                            EmailRenderTrace.d(
                                traceMail,
                                "WV",
                                "WV_SCROLL_RESTORE_POSTED",
                                "loadKey=${document.key} scrollY=${savedScrollY.intValue}"
                            )
                            webView.post {
                                if (released.value || activeLoadKey != document.key) {
                                    EmailRenderTrace.d(
                                        traceMail,
                                        "WV",
                                        "WV_PAGE_RENDERED_IGNORED",
                                        "loadKey=${document.key} reason=stale_after_post"
                                    )
                                    return@post
                                }
                                webView.scrollTo(0, savedScrollY.intValue)
                                webView.invalidate()
                                EmailRenderTrace.d(
                                    traceMail,
                                    "WV",
                                    "WV_SCROLL_RESTORE_APPLIED",
                                    "loadKey=${document.key} scrollY=${savedScrollY.intValue}"
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
                                "loadKey=${document.key} activeLoadKey=$activeLoadKey " +
                                    "released=${released.value} reason=stale_or_released"
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
                } else if (loggedSkippedKey != document.key) {
                    loggedSkippedKey = document.key
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
                    "loadKey=${activeLoadKey ?: "none"} scrollY=${webView.scrollY}"
                )
                savedScrollY.intValue = webView.scrollY
                released.value = true
                activeLoadKey = null
                webViewRef.value = null
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── UrlInterception (D7) ────────────────────────────────────────────

private class CustomTabsWebViewClient(
    private val ctx: android.content.Context,
    private val traceMail: String,
    private val loadKey: String,
    private val onPageReady: () -> Unit
) : WebViewClient() {
    companion object {
        private const val TAG = "CustomTabsWebViewClient"
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        EmailRenderTrace.d(traceMail, "WV", "WV_PAGE_STARTED", "loadKey=$loadKey")
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        EmailRenderTrace.d(traceMail, "WV", "WV_COMMIT_VISIBLE", "loadKey=$loadKey")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        EmailRenderTrace.d(traceMail, "WV", "WV_PAGE_FINISHED", "loadKey=$loadKey")
        EmailRenderTrace.d(traceMail, "WV", "WV_VISUAL_REQUESTED", "loadKey=$loadKey")
        view?.postVisualStateCallback(
            0L,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    EmailRenderTrace.d(
                        traceMail,
                        "WV",
                        "WV_VISUAL_CALLBACK",
                        "loadKey=$loadKey requestId=$requestId"
                    )
                    onPageReady()
                }
            }
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return true
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(ctx, android.net.Uri.parse(url))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open link via modern WebView API", e)
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val safeUrl = url ?: return true
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(ctx, android.net.Uri.parse(safeUrl))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open link via legacy WebView API", e)
        }
        return true
    }
}
