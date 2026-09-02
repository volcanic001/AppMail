package com.david.mailapp.data.cleaner

import android.os.SystemClock
import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val CLEAN_TAG = "MailPerfTrace"
private fun cleanNow() = SystemClock.elapsedRealtime()

/**
 * Pure (Compose-free) normalizer for raw email HTML bodies.
 *
 * Strips sender-imposed backgrounds, hardcoded text colors, opacity and
 * color-scheme hints so the WebView can adopt the app theme cleanly, while
 * preserving layout (width, margin, padding, borders, fonts, tables).
 *
 * Evaluates simple HTML structure and integrates margin wrapping internally so
 * visual layout decisions are stored once inside [Email.cleanBody].
 */
internal object EmailHtmlCleaner {

    val STRIPPED_PROPERTIES: Set<String> = setOf(
        "background",
        "background-color",
        "color",
        "-webkit-text-fill-color",
        "opacity"
    )

    /**
     * Normalizes [html], returning the cleaned and margin-wrapped body fragment.
     * On any parse failure or empty output for non-empty input, returns [html] (fail-open).
     */
    fun clean(html: String): String {
        if (html.isBlank()) return ""
        if (html.length > 2_000_000) return html
        return try {
            val t0 = cleanNow()
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] START htmlLen=${html.length}")
            val doc: Document = Jsoup.parseBodyFragment(html)
            val tParse = cleanNow()
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] PARSE_DONE parseMs=${tParse - t0}")
            val isSimple = isSimpleHtml(doc)
            val cleanedBody = clean(doc)
            val wrappedBody = if (isSimple) {
                """<div style="margin:0 16px; padding-top: 20px;">$cleanedBody</div>"""
            } else {
                cleanedBody
            }
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] DONE outputLen=${wrappedBody.length} totalMs=${cleanNow() - t0}")
            wrappedBody.ifBlank { html }
        } catch (e: Exception) {
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] ERROR error=${e.javaClass.simpleName}")
            html
        }
    }

    /**
     * Normalizes the already-parsed [doc] in-place and returns the cleaned body fragment.
     */
    fun clean(doc: Document): String {
        // Remove bgcolor and background attributes
        doc.select("[bgcolor]").removeAttr("bgcolor")
        doc.select("[background]").removeAttr("background")
        // Remove color attribute from legacy font elements
        doc.select("font[color]").removeAttr("color")

        // Remove legacy body text/link attributes
        doc.select("body").forEach { body ->
            body.removeAttr("text")
            body.removeAttr("link")
            body.removeAttr("vlink")
            body.removeAttr("alink")
        }

        // Remove theme-related meta tags
        doc.select("meta").forEach { meta ->
            val name = meta.attr("name").lowercase()
            if (name == "color-scheme" || name == "supported-color-schemes") {
                meta.remove()
            }
        }

        // Remove color-scheme attributes on html element
        doc.select("html").forEach { htmlEl ->
            htmlEl.removeAttr("color-scheme")
            htmlEl.removeAttr("supported-color-schemes")
        }

        // Clean inline styles
        doc.select("[style]").forEach { element ->
            val styleAttr = element.attr("style")
            val styles = styleAttr.split(";").map { it.trim() }
            val cleanedStyles = styles.filter { style ->
                val trimmed = style.trim()
                if (trimmed.isEmpty()) return@filter false
                !isStrippedProperty(trimmed)
            }
            if (cleanedStyles.isEmpty()) {
                element.removeAttr("style")
            } else {
                element.attr("style", cleanedStyles.joinToString("; "))
            }
        }

        // Clean style tags
        doc.select("style").forEach { styleEl ->
            val originalCss = styleEl.data()
            val cleanedCss = cleanCssText(originalCss)
            styleEl.html(cleanedCss)
        }

        return doc.body().html()
    }

    /**
     * Heuristic to distinguish simple HTML emails from complex layout emails.
     */
    fun isSimpleHtml(doc: Document): Boolean {
        return doc.select("table table").isEmpty()
    }

    private fun isStrippedProperty(rule: String): Boolean {
        val colonIdx = rule.indexOf(':')
        if (colonIdx == -1) return false
        val propName = rule.substring(0, colonIdx).trim().lowercase()
        return propName in STRIPPED_PROPERTIES
    }

    private fun cleanCssText(css: String): String {
        val withoutComments = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val withoutMedia = removePrefersColorSchemeMediaQueries(withoutComments)
        return cleanBackgroundProperties(withoutMedia)
    }

    private fun removePrefersColorSchemeMediaQueries(css: String): String {
        val result = StringBuilder()
        var i = 0
        val n = css.length
        while (i < n) {
            if (css.startsWith("@media", i, ignoreCase = true)) {
                var j = i + 6
                while (j < n && css[j] != '{') {
                    j++
                }
                if (j < n) {
                    val header = css.substring(i, j)
                    if (header.contains("prefers-color-scheme", ignoreCase = true)) {
                        var braceCount = 1
                        j++
                        while (j < n && braceCount > 0) {
                            if (css[j] == '{') {
                                braceCount++
                            } else if (css[j] == '}') {
                                braceCount--
                            }
                            j++
                        }
                        i = j
                        continue
                    }
                }
            }
            result.append(css[i])
            i++
        }
        return result.toString()
    }

    private fun cleanBackgroundProperties(css: String): String {
        val result = StringBuilder()
        var i = 0
        val n = css.length
        while (i < n) {
            if (css[i] == '{') {
                result.append('{')
                i++
                var braceCount = 1
                var j = i
                var hasNested = false
                while (j < n && braceCount > 0) {
                    if (css[j] == '{') {
                        hasNested = true
                        braceCount++
                    } else if (css[j] == '}') {
                        braceCount--
                    }
                    j++
                }
                if (j > i) {
                    val blockContent = css.substring(i, j - 1)
                    if (hasNested) {
                        result.append(cleanBackgroundProperties(blockContent))
                    } else {
                        val rules = blockContent.split(";")
                        val cleanedRules = mutableListOf<String>()
                        for (rule in rules) {
                            val trimmed = rule.trim()
                            if (trimmed.isEmpty()) continue
                            if (isStrippedProperty(trimmed)) continue
                            cleanedRules.add(rule)
                        }
                        result.append(cleanedRules.joinToString(";"))
                        if (cleanedRules.isNotEmpty()) {
                            result.append(";")
                        }
                    }
                    i = j
                    result.append('}')
                }
            } else {
                result.append(css[i])
                i++
            }
        }
        return result.toString()
    }
}
