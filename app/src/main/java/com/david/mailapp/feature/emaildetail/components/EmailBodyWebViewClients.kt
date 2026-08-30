package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import com.david.mailapp.feature.emaildetail.EmailRenderTrace

internal class CustomTabsWebViewClient(
    private val ctx: Context,
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
                .launchUrl(ctx, Uri.parse(url))
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
                .launchUrl(ctx, Uri.parse(safeUrl))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open link via legacy WebView API", e)
        }
        return true
    }
}

internal class TraceWebChromeClient(
    private val traceMail: String,
    private val loadKey: String
) : WebChromeClient() {
    private var lastMilestone = -1

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        val milestone = when {
            newProgress >= 100 -> 100
            newProgress >= 75 -> 75
            newProgress >= 50 -> 50
            newProgress >= 25 -> 25
            else -> 0
        }
        if (milestone != lastMilestone) {
            lastMilestone = milestone
            EmailRenderTrace.d(
                traceMail,
                "WV",
                "WV_PROGRESS",
                "loadKey=$loadKey progress=$newProgress milestone=$milestone"
            )
        }
    }
}
