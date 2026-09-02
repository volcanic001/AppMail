package com.david.mailapp.feature.emaildetail.components

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.david.mailapp.BuildConfig
import com.david.mailapp.core.perf.MailOpenPerformanceTrace

@Composable
internal fun EmailPlainTextBody(
    text: String,
    traceMail: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotatedString = buildAnnotatedStringWithLinks(text, primaryColor, onOpenLink)

    LaunchedEffect(text, traceMail) {
        withFrameNanos { _ ->
            MailOpenPerformanceTrace.onVisualReady(traceMail)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("email_detail_plain_text_body")
    ) {
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
        ) {
            Text(
                text = annotatedString,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.5.sp,
                letterSpacing = 0.sp
            )
        }

        if (BuildConfig.PERF_TRACE_ENABLED) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("email_detail_visual_ready")
            )
        }
    }
}

internal fun buildAnnotatedStringWithLinks(
    text: String,
    primaryColor: Color,
    onOpenLink: (String) -> Unit
): AnnotatedString {
    val spannable = SpannableString(text)
    LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS)
    val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)

    return buildAnnotatedString {
        append(text)
        for (span in urlSpans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val url = span.url
            if (start in 0..text.length && end in 0..text.length && start < end) {
                addLink(
                    url = LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { annotation ->
                            val linkUrl = (annotation as? LinkAnnotation.Url)?.url
                            if (linkUrl != null) {
                                onOpenLink(linkUrl)
                            }
                        }
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}
