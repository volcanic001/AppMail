package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.webkit.WebView
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.lang.ref.WeakReference

internal fun updateEmailBodyWebView(
    webView: WebView,
    document: PreparedDocument?,
    currentKey: String?,
    context: Context,
    surfaceArgb: Int,
    showImages: Boolean,
    isDark: Boolean,
    runtimeState: EmailBodyWebViewRuntimeState,
    traceMail: String,
    onPageRendered: (() -> Unit)?
) {
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
        val isProgressiveReload = runtimeState.lastLoaded.value != null
        if (isProgressiveReload) {
            val capturedScrollY = webView.scrollY
            runtimeState.savedScrollY.intValue = capturedScrollY
            EmailRenderTrace.d(
                traceMail,
                "WV",
                "WV_PROGRESSIVE_SCROLL_CAPTURE",
                "loadKey=${document.key} scrollY=$capturedScrollY"
            )
        }

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

        val enableAutoImages = runtimeState.initialVisualReady.value && showImages
        webView.settings.applyHardening(showImages, isDark, loadsImagesAutomatically = enableAutoImages)

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
                    if (isProgressiveReload) {
                        EmailRenderTrace.d(
                            traceMail,
                            "WV",
                            "WV_PROGRESSIVE_SCROLL_RESTORE",
                            "loadKey=${document.key} scrollY=${runtimeState.savedScrollY.intValue}"
                        )
                    } else {
                        EmailRenderTrace.d(
                            traceMail,
                            "WV",
                            "WV_SCROLL_RESTORE_APPLIED",
                            "loadKey=${document.key} scrollY=${runtimeState.savedScrollY.intValue}"
                        )
                    }

                    if (!runtimeState.initialVisualReady.value) {
                        runtimeState.initialVisualReady.value = true
                        if (showImages) {
                            webView.settings.loadsImagesAutomatically = true
                        }
                    }

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
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.beginSection(
            com.david.mailapp.core.perf.MailOpenPerformanceTrace.SECTION_WEBVIEW_VISUAL,
            traceMail
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
}

internal fun releaseEmailBodyWebView(
    webView: WebView,
    runtimeState: EmailBodyWebViewRuntimeState,
    traceMail: String
) {
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
}
