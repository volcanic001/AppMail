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
