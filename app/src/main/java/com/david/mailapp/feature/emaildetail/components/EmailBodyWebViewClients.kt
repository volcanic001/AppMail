package com.david.mailapp.feature.emaildetail.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import com.david.mailapp.feature.emaildetail.EmailRenderTrace

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
