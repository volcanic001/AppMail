package com.david.mailapp.feature.emaildetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun buildHtml_simpleHtml_wrapsCleanBodyWithSimpleMargins() {
        val html = buildHtml(
            body = "<div style=\"margin:0 16px; padding-top: 20px;\"><p>hola</p></div>",
            showImages = true,
            isDark = false,
            surfaceArgb = 0xFFFFFBFE.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFF6750A4.toInt()
        )
        assertTrue(html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">"))
        assertTrue(html.contains("<div style=\"margin:0 16px; padding-top: 20px;\"><p>hola</p></div>"))
        assertTrue(html.contains("font-size: 15px"))
        assertTrue(html.contains("line-height: 1.5"))
        assertTrue(html.contains("<body>"))
    }

    @Test
    fun buildHtml_newsletterDoesNotAddSimpleWrapper() {
        val newsletter =
            "<table><tr><td><table><tr><td>inner</td></tr></table></td></tr></table>"
        val html = buildHtml(
            body = newsletter,
            showImages = true,
            isDark = false,
            surfaceArgb = 0xFFFFFBFE.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFF6750A4.toInt()
        )
        assertFalse(html.contains("padding-top: 20px"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("inner"))
    }

    @Test
    fun buildHtml_darkAndLightThemesPreserveTextAndColorScheme() {
        val dark = buildHtml(
            body = "<p>t</p>",
            showImages = true,
            isDark = true,
            surfaceArgb = 0xFF1C1B1F.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFFD0BCFF.toInt()
        )
        assertTrue(dark.contains("color-scheme: dark"))
        assertTrue(dark.contains("--text: rgb(224, 224, 224)"))
        assertTrue(dark.contains("--bg: rgb(28,27,31)"))
        assertTrue(dark.contains("--link: rgb(208,188,255)"))

        val light = buildHtml(
            body = "<p>t</p>",
            showImages = true,
            isDark = false,
            surfaceArgb = 0xFFFFFBFE.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFF6750A4.toInt()
        )
        assertTrue(light.contains("color-scheme: light"))
        assertTrue(light.contains("--text: rgb(33, 33, 33)"))
        assertTrue(light.contains("--bg: rgb(255,251,254)"))
        assertTrue(light.contains("--link: rgb(103,80,164)"))
    }

    @Test
    fun buildHtml_whenImagesBlocked_hidesRemoteButNotDataImages() {
        val html = buildHtml(
            body = "<img src=\"https://example.com/a.png\"><img src=\"data:image/png;base64,AAA\">",
            showImages = false,
            isDark = false,
            surfaceArgb = 0xFFFFFBFE.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFF6750A4.toInt()
        )
        assertTrue(
            html.contains("img:not([src^=\"data:\"]){display:none!important}")
        )
        assertTrue(html.contains("data:image/png;base64,AAA"))
    }

    @Test
    fun buildHtml_whenImagesEnabled_doesNotInjectRemoteHideRule() {
        val html = buildHtml(
            body = "<p>sin imagenes</p>",
            showImages = true,
            isDark = false,
            surfaceArgb = 0xFFFFFBFE.toInt(),
            onSurfaceArgb = 0xFF1B1B1B.toInt(),
            primaryArgb = 0xFF6750A4.toInt()
        )
        assertFalse(html.contains("img:not([src^=\"data:\"])"))
    }
}
