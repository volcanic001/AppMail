package com.david.mailapp.feature.emaildetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlCleanerTest {

    @Test
    fun `removes bgcolor and background attributes`() {
        val html = """<table bgcolor="#000000" background="img.png"><td bgcolor="#111">x</td></table>"""
        val out = EmailHtmlCleaner.clean(html)
        assertFalse(out.contains("bgcolor"))
        assertFalse(out.contains("background="))
    }

    @Test
    fun `neutralizes legacy font color`() {
        val out = EmailHtmlCleaner.clean("""<font color="#ff0000">hello</font>""")
        assertFalse(out.contains("color="))
        assertTrue(out.contains("hello"))
    }

    @Test
    fun `strips only target properties from inline style and preserves layout`() {
        val html =
            """<p style="color:#777; opacity:0.65; margin:10px; font-size:14px; padding:4px">t</p>"""
        val out = EmailHtmlCleaner.clean(html)
        assertFalse(out.contains("color:"))
        assertFalse(out.contains("opacity"))
        // Layout must survive
        assertTrue(out.contains("margin:10px"))
        assertTrue(out.contains("font-size:14px"))
        assertTrue(out.contains("padding:4px"))
    }

    @Test
    fun `removes style attribute entirely when only stripped props remain`() {
        val out = EmailHtmlCleaner.clean("""<span style="color:#fff; opacity:0.8">hi</span>""")
        assertFalse(out.contains("style="))
        assertTrue(out.contains("hi"))
    }

    @Test
    fun `removes prefers-color-scheme media queries from style blocks`() {
        val html = """
            <style>
              @media (prefers-color-scheme: dark) { body { background:#000; color:#fff } }
              .keep { margin: 4px; }
            </style>
            <p class="keep">x</p>
        """.trimIndent()
        val out = EmailHtmlCleaner.clean(html)
        assertFalse(out.contains("prefers-color-scheme"))
        assertTrue(out.contains("margin: 4px"))
    }

    @Test
    fun `strips background and color inside style block rules but keeps layout`() {
        val html =
            """<style>.a { background:#fff; color:#333; padding:8px; border:1px solid #ccc }</style>"""
        val out = EmailHtmlCleaner.clean(html)
        assertFalse(out.contains("background"))
        assertFalse(out.contains("color:"))
        assertTrue(out.contains("padding:8px"))
        assertTrue(out.contains("border:1px solid #ccc"))
    }

    @Test
    fun `removes theme meta tags`() {
        val html = """<meta name="color-scheme" content="light dark"><p>x</p>"""
        val out = EmailHtmlCleaner.clean(html)
        assertFalse(out.contains("color-scheme"))
    }

    @Test
    fun `single source of truth lists the expected stripped properties`() {
        assertEquals(
            setOf("background", "background-color", "color", "-webkit-text-fill-color", "opacity"),
            EmailHtmlCleaner.STRIPPED_PROPERTIES,
        )
    }

    @Test
    fun `returns input unchanged on empty string`() {
        assertEquals("", EmailHtmlCleaner.clean(""))
    }
}
