package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI

internal object SafeLinkPolicy {
    private const val TAG = "SafeLinkPolicy"

    /**
     * Checks if a URL string is valid and safe according to security criteria:
     * - Rejects null, blank, or strings containing control characters.
     * - Normalizes links starting with "www." to "http://www.".
     * - Requires scheme to be "http" or "https".
     * - Requires host to be present and non-empty.
     * - Rejects malformed syntax or unsafe schemes (javascript, file, data, mailto, etc.).
     */
    fun isValidUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        if (rawUrl.any { it.isISOControl() }) return false

        var urlString = rawUrl.trim()
        if (urlString.startsWith("www.", ignoreCase = true)) {
            urlString = "http://$urlString"
        }

        return try {
            val javaUri = URI(urlString)
            val scheme = javaUri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return false
            }
            !javaUri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalizes a URL string if valid, returning "http://www..." if started with "www.".
     */
    fun normalizeUrl(rawUrl: String?): String? {
        if (!isValidUrl(rawUrl)) return null
        var urlString = rawUrl!!.trim()
        if (urlString.startsWith("www.", ignoreCase = true)) {
            urlString = "http://$urlString"
        }
        return urlString
    }

    fun sanitizeAndValidate(rawUrl: String?): Uri? {
        val normalized = normalizeUrl(rawUrl) ?: return null
        return try {
            Uri.parse(normalized)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Opens a URL string via Chrome Custom Tabs if it passes safety validation.
     * Log messages record only error categories, never URL contents or error details.
     */
    fun openSafeUrl(context: Context, rawUrl: String?): Boolean {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            Log.w(TAG, "Link open rejected: unsafe_scheme_or_malformed")
            return false
        }

        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(normalized))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Link open failed: ${e.javaClass.simpleName}")
            false
        }
    }
}
