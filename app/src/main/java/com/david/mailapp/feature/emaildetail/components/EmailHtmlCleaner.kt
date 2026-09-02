package com.david.mailapp.feature.emaildetail.components

import android.os.SystemClock
import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// DEBUG_PERF
private const val CLEAN_TAG = "MailPerfTrace"
private fun cleanNow() = SystemClock.elapsedRealtime()

/**
 * Pure (Compose-free) normalizer for raw email HTML bodies.
 *
 * Strips sender-imposed backgrounds, hardcoded text colors, opacity and
 * color-scheme hints so the WebView can adopt the app theme cleanly, while
 * preserving layout (width, margin, padding, borders, fonts, tables).
 *
 * Runs unconditionally in both light and dark modes. Kept free of Android /
 * Compose dependencies so it can be unit tested in isolation.
 */
internal object EmailHtmlCleaner {

    /**
     * CSS properties removed everywhere (inline `style="..."` attributes and
     * internal `<style>` blocks). Single source of truth — adding a property
     * here strips it in every code path.
     */
    val STRIPPED_PROPERTIES: Set<String> = setOf(
        "background",
        "background-color",
        "color",
        "-webkit-text-fill-color",
        "opacity",
    )

    /**
     * Normalizes [html], returning the cleaned body fragment. On any parse
     * failure the original input is returned unchanged (fail-open).
     */
    fun clean(html: String): String {
        return try {
            val t0 = cleanNow()
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] START htmlLen=${html.length}")
            val doc: Document = Jsoup.parseBodyFragment(html)
            val tParse = cleanNow()
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] PARSE_DONE parseMs=${tParse - t0}")
            val result = clean(doc)
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] DONE outputLen=${result.length} totalMs=${cleanNow() - t0}")
            result
        } catch (e: Exception) {
            Log.d(CLEAN_TAG, "[JSOUP_CLEAN] ERROR error=${e.javaClass.simpleName}")
            html
        }
    }

    /**
     * Normalizes the already-parsed [doc] in-place and returns the cleaned body fragment.
     * Use this overload together with [isSimpleHtml] to reuse a single Jsoup parse for both
     * classification and cleaning, avoiding a redundant second parse.
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
     * Heuristic to distinguish simple HTML emails (plain text + inline images, like Gmail
     * compose) from professionally laid-out emails (newsletters, transactional).
     *
     * Returns `true` when the document is structurally simple and should receive the
     * app's default horizontal margin for visual consistency with Compose headers.
     */
    fun isSimpleHtml(doc: Document): Boolean {
        return doc.select("table table").isEmpty()
    }

    /** True if the CSS declaration [rule] targets a stripped property. */
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
                        j++ // skip '{'
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
