package com.david.mailapp.feature.emaildetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EmailBodyDocumentTest {

    @Test
    fun buildLoadKey_preservesExactComponentOrder() {
        val body = "<html>order</html>"
        val showImages = true
        val isDark = false
        val surfaceArgb = 0xFF0F1115.toInt()
        val onSurfaceArgb = 0xFF1B1B1B.toInt()
        val primaryArgb = 0xFF6750A4.toInt()

        val key = buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)

        val expected =
            "${body.hashCode()}_${showImages}_${isDark}_${surfaceArgb}_${onSurfaceArgb}_${primaryArgb}"
        assertEquals(expected, key)
    }

    @Test
    fun buildLoadKey_changesWhenEachComponentChanges() {
        val body = "<html>base</html>"
        val showImages = true
        val isDark = false
        val surfaceArgb = 1
        val onSurfaceArgb = 2
        val primaryArgb = 3
        val base = buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)

        assertNotEquals(base, buildLoadKey("<html>other</html>", showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb))
        assertNotEquals(base, buildLoadKey(body, !showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb))
        assertNotEquals(base, buildLoadKey(body, showImages, !isDark, surfaceArgb, onSurfaceArgb, primaryArgb))
        assertNotEquals(base, buildLoadKey(body, showImages, isDark, surfaceArgb + 1, onSurfaceArgb, primaryArgb))
        assertNotEquals(base, buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb + 1, primaryArgb))
        assertNotEquals(base, buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb + 1))
    }

    @Test
    fun toCssRgb_ignoresAlphaAndFormatsRgb() {
        assertEquals("rgb(255,0,0)", toCssRgb(0xFFFF0000.toInt()))
        assertEquals("rgb(28,27,31)", toCssRgb(0xFF1C1B1F.toInt()))
        assertEquals("rgb(103,80,164)", toCssRgb(0xFF6750A4.toInt()))
        assertEquals(toCssRgb(0xFF112233.toInt()), toCssRgb(0x00112233.toInt()))
    }

    @Test
    fun preparedDocument_storesKeyAndHtml() {
        val doc = PreparedDocument("key123", "<html>body</html>")
        assertEquals("key123", doc.key)
        assertEquals("<html>body</html>", doc.html)
    }
}
