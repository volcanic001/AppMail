package com.david.mailapp.core.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewStartupStateControllerTest {

    @Test
    fun `begin is single-flight and success is terminal`() {
        val controller = WebViewStartupStateController()

        assertTrue(controller.begin())
        assertTrue(controller.state.value is WebViewStartupState.Starting)
        assertFalse(controller.begin())

        controller.complete()

        assertTrue(controller.state.value is WebViewStartupState.Ready)
        assertFalse(controller.begin())
        assertFalse(controller.begin(forceRetry = true))
    }

    @Test
    fun `failure requires explicit retry`() {
        val controller = WebViewStartupStateController()
        controller.begin()
        controller.fail(IllegalStateException("startup"))

        val failed = controller.state.value
        assertTrue(failed is WebViewStartupState.Failed)
        assertFalse(controller.begin())
        assertTrue(controller.begin(forceRetry = true))
        assertTrue(controller.state.value is WebViewStartupState.Starting)
    }
}
