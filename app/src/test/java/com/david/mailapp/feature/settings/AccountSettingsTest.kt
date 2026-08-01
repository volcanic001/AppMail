package com.david.mailapp.feature.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountSettingsTest {

    @Test
    fun `cancelación conserva identidad y no produce resultado ausente`() = runTest {
        val sentinel = CancellationException("sentinel-account")

        try {
            loadAccountEmail { throw sentinel }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertTrue("Must propagate exact sentinel instance", e === sentinel)
        }
    }

    @Test
    fun `error ordinario continua devolviendo null`() = runTest {
        val result = loadAccountEmail { throw RuntimeException("ordinary error") }
        assertNull(result)
    }
}
