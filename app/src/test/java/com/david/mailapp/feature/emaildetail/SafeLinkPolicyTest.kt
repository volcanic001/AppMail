package com.david.mailapp.feature.emaildetail

import com.david.mailapp.feature.emaildetail.components.SafeLinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLinkPolicyTest {

    @Test
    fun `accepts valid http and https urls`() {
        assertTrue(SafeLinkPolicy.isValidUrl("http://example.com/path?arg=1"))
        assertEquals("http://example.com/path?arg=1", SafeLinkPolicy.normalizeUrl("http://example.com/path?arg=1"))

        assertTrue(SafeLinkPolicy.isValidUrl("https://sub.domain.org/page#anchor"))
        assertEquals("https://sub.domain.org/page#anchor", SafeLinkPolicy.normalizeUrl("https://sub.domain.org/page#anchor"))
    }

    @Test
    fun `normalizes www prefix to http`() {
        assertTrue(SafeLinkPolicy.isValidUrl("www.android.com/news"))
        assertEquals("http://www.android.com/news", SafeLinkPolicy.normalizeUrl("www.android.com/news"))
    }

    @Test
    fun `rejects null blank or control character strings`() {
        assertFalse(SafeLinkPolicy.isValidUrl(null))
        assertFalse(SafeLinkPolicy.isValidUrl(""))
        assertFalse(SafeLinkPolicy.isValidUrl("   "))
        assertFalse(SafeLinkPolicy.isValidUrl("https://example.com\u0000/bad"))
        assertFalse(SafeLinkPolicy.isValidUrl("http://example.com\u0007/bad"))
    }

    @Test
    fun `rejects urls with missing or blank host`() {
        assertFalse(SafeLinkPolicy.isValidUrl("http:///path"))
        assertFalse(SafeLinkPolicy.isValidUrl("https://"))
        assertFalse(SafeLinkPolicy.isValidUrl("http://"))
    }

    @Test
    fun `rejects non-web and unsafe schemes`() {
        assertFalse(SafeLinkPolicy.isValidUrl("javascript:alert(1)"))
        assertFalse(SafeLinkPolicy.isValidUrl("data:text/html,<h1>bad</h1>"))
        assertFalse(SafeLinkPolicy.isValidUrl("file:///sdcard/secret.txt"))
        assertFalse(SafeLinkPolicy.isValidUrl("content://media/external"))
        assertFalse(SafeLinkPolicy.isValidUrl("mailto:user@example.com"))
        assertFalse(SafeLinkPolicy.isValidUrl("tel:1234567890"))
    }

    @Test
    fun `rejects malformed urls`() {
        assertFalse(SafeLinkPolicy.isValidUrl("http://:8080/bad"))
        assertFalse(SafeLinkPolicy.isValidUrl("http://[invalid-ipv6/path"))
    }
}
