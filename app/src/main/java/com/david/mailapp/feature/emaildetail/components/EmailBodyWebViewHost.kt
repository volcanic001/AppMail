package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.lang.ref.WeakReference

internal fun EmailBodyWebViewHost(
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
