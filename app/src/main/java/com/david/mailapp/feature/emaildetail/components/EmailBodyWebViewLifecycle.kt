package com.david.mailapp.feature.emaildetail.components

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.david.mailapp.feature.emaildetail.EmailRenderTrace

@Composable
internal fun BindEmailBodyWebViewLifecycle(
    lifecycleOwner: LifecycleOwner,
    runtimeState: EmailBodyWebViewRuntimeState,
    traceMail: String
) {
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
}
