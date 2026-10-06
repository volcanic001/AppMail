package com.david.mailapp.core.html

import com.david.mailapp.domain.model.EmailInlineReference

/**
 * Ensures that every downloadable MIME image has a corresponding element in
 * the HTML. Some senders omit Content-ID even for an image placed in the body.
 */
internal fun ensureInlineImageTags(
    html: String,
    references: List<EmailInlineReference>
): String {
    val missing = references.filterNot { reference ->
        containsCidReference(html, reference.contentId)
    }
    if (missing.isEmpty()) return html

    return buildString(html.length + missing.size * 64) {
        append(html)
        append("<div class=\"mailapp-inline-images\">")
        missing.forEach { reference ->
            append("<img src=\"cid:")
            append(escapeHtmlAttribute(reference.contentId))
            append("\" alt=\"\">")
        }
        append("</div>")
    }
}

internal fun appendEmbeddedImageTags(
    html: String,
    dataUris: List<String>
): String {
    val missing = dataUris.filterNot { dataUri -> html.contains(dataUri) }
    if (missing.isEmpty()) return html

    return buildString(html.length + missing.sumOf(String::length)) {
        append(html)
        append("<div class=\"mailapp-inline-images\">")
        missing.forEach { dataUri ->
            append("<img src=\"")
            append(dataUri)
            append("\" alt=\"\">")
        }
        append("</div>")
    }
}

private fun containsCidReference(html: String, contentId: String): Boolean =
    html.contains("cid:$contentId", ignoreCase = true) ||
        html.contains("cid:&lt;$contentId&gt;", ignoreCase = true) ||
        html.contains("cid:<$contentId>", ignoreCase = true)

private fun escapeHtmlAttribute(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
