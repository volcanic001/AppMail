package com.david.mailapp.feature.emaildetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailBodyWebViewRuntimeTest {

    @Test
    fun `renderer retry creates one fresh generation and preserves scroll`() {
        val runtime = EmailBodyWebViewRuntimeState().apply {
            savedScrollY.intValue = 420
            lastLoaded.value = "old"
            activeLoadKey.value = "old"
            released.value = true
            initialVisualReady.value = true
            rendererFailure.value = RendererFailure(didCrash = false, canRetry = true)
        }

        runtime.retryRenderer()

        assertEquals(1, runtime.instanceGeneration.intValue)
        assertEquals(1, runtime.rendererReloadAttempts.intValue)
        assertEquals(420, runtime.savedScrollY.intValue)
        assertNull(runtime.rendererFailure.value)
        assertNull(runtime.lastLoaded.value)
        assertNull(runtime.activeLoadKey.value)
        assertFalse(runtime.released.value)
        assertFalse(runtime.initialVisualReady.value)
        assertTrue(runtime.recoveryInProgress.value)
    }

    @Test
    fun `non retryable renderer failure cannot create another generation`() {
        val runtime = EmailBodyWebViewRuntimeState().apply {
            rendererFailure.value = RendererFailure(didCrash = true, canRetry = false)
        }

        runtime.retryRenderer()

        assertEquals(0, runtime.instanceGeneration.intValue)
        assertEquals(0, runtime.rendererReloadAttempts.intValue)
        assertTrue(runtime.rendererFailure.value?.didCrash == true)
    }
}
