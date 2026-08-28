package com.david.mailapp.feature.emaildetail.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberPreparedEmailBodyDocument(
    body: String?,
    currentKey: String?,
    showImages: Boolean,
    isDark: Boolean,
    surfaceArgb: Int,
    onSurfaceArgb: Int,
    primaryArgb: Int,
    traceMail: String
): PreparedDocument? {
    // HTML parsing and Jsoup cleanup are CPU-heavy for large inline images.
    // Prepare the document off-main while the already-mounted WebView remains
    // hidden behind the Compose loader.
    var preparedDocument by remember(currentKey) {
        mutableStateOf<PreparedDocument?>(null)
    }
    LaunchedEffect(currentKey) {
        val sourceBody = body
        val loadKey = currentKey
        if (sourceBody == null || loadKey == null) {
            EmailRenderTrace.d(traceMail, "WV", "HTML_BUILD_WAITING", "reason=body_pending")
            return@LaunchedEffect
        }

        val html = withContext(Dispatchers.Default) {
            val startedAt = EmailRenderTrace.now()
            EmailRenderTrace.d(
                traceMail,
                "WV",
                "HTML_BUILD_START",
                "loadKey=$loadKey bodyLen=${sourceBody.length}"
            )
            buildHtml(
                sourceBody,
                showImages,
                isDark,
                surfaceArgb,
                onSurfaceArgb,
                primaryArgb
            ).also { result ->
                EmailRenderTrace.d(
                    traceMail,
                    "WV",
                    "HTML_BUILD_END",
                    "loadKey=$loadKey htmlLen=${result.length} " +
                        "durationMs=${EmailRenderTrace.now() - startedAt}"
                )
            }
        }

        preparedDocument = PreparedDocument(loadKey, html)
        EmailRenderTrace.d(
            traceMail,
            "WV",
            "HTML_BUILD_READY",
            "loadKey=$loadKey htmlLen=${html.length}"
        )
    }
    return preparedDocument
}
