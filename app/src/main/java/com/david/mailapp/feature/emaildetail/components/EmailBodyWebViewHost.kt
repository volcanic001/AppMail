package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.lang.ref.WeakReference

@Composable
internal fun EmailBodyWebViewHost(
    context: Context,
    currentKey: String?,
    preparedDocument: PreparedDocument?,
    surfaceArgb: Int,
    showImages: Boolean,
    isDark: Boolean,
    runtimeState: EmailBodyWebViewRuntimeState,
    traceMail: String,
    onPageRendered: (() -> Unit)?,
    onImageLongPress: ((imageUrl: String) -> Unit)?,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                createEmailBodyWebViewHost(
                    context = ctx,
                    runtimeState = runtimeState,
                    traceMail = traceMail,
                    currentKey = currentKey,
                    surfaceArgb = surfaceArgb,
                    showImages = showImages,
                    isDark = isDark,
                    onImageLongPress = onImageLongPress
                )
            },
            update = { webView ->
                updateEmailBodyWebView(
                    webView = webView,
                    document = preparedDocument,
                    currentKey = currentKey,
                    context = context,
                    surfaceArgb = surfaceArgb,
                    showImages = showImages,
                    isDark = isDark,
                    runtimeState = runtimeState,
                    traceMail = traceMail,
                    onPageRendered = onPageRendered
                )
            },
            onRelease = { webView ->
                releaseEmailBodyWebView(
                    webView = webView,
                    runtimeState = runtimeState,
                    traceMail = traceMail
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun createEmailBodyWebViewHost(
    context: Context,
    runtimeState: EmailBodyWebViewRuntimeState,
    traceMail: String,
    currentKey: String?,
    surfaceArgb: Int,
    showImages: Boolean,
    isDark: Boolean,
    onImageLongPress: ((imageUrl: String) -> Unit)?
): WebView =
    WebView(context).apply {
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
