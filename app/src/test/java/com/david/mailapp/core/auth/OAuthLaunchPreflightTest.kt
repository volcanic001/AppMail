package com.david.mailapp.core.auth

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfCacheClearResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthLaunchPreflightTest {

    @Test
    fun noPendingCleanup_returnsReadyWithoutClearing() = runTest {
        var clearCalled = false
        var markCalled = false
        val result = runOAuthLaunchPreflight(
            isPendingPdfCleanup = { false },
            clearPdfCache = { clearCalled = true; PdfCacheClearResult.Success },
            markPdfCleanupCompleted = { markCalled = true }
        )
        assertTrue("Expected Ready", result is OAuthLaunchPreflightResult.Ready)
        assertTrue("clearPdfCache must not be called", !clearCalled)
        assertTrue("markPdfCleanupCompleted must not be called", !markCalled)
    }

    @Test
    fun successfulCleanup_clearsMarkerAndReturnsReady() = runTest {
        var clearCalled = false
        var markCalled = false
        val result = runOAuthLaunchPreflight(
            isPendingPdfCleanup = { true },
            clearPdfCache = { clearCalled = true; PdfCacheClearResult.Success },
            markPdfCleanupCompleted = { markCalled = true }
        )
        assertTrue("Expected Ready", result is OAuthLaunchPreflightResult.Ready)
        assertTrue("clearPdfCache must be called", clearCalled)
        assertTrue("markPdfCleanupCompleted must be called", markCalled)
    }

    @Test
    fun typedCleanupFailure_keepsMarkerAndReturnsTempCleanupFailure() = runTest {
        var clearCalled = false
        var markCalled = false
        val result = runOAuthLaunchPreflight(
            isPendingPdfCleanup = { true },
            clearPdfCache = { clearCalled = true; PdfCacheClearResult.Failure(listOf("err")) },
            markPdfCleanupCompleted = { markCalled = true }
        )
        assertTrue("Expected Failed", result is OAuthLaunchPreflightResult.Failed)
        assertEquals(UiErrorReason.TEMP_CLEANUP_FAILED, (result as OAuthLaunchPreflightResult.Failed).reason)
        assertTrue("clearPdfCache must be called", clearCalled)
        assertTrue("markPdfCleanupCompleted must NOT be called", !markCalled)
    }

    @Test
    fun ordinaryException_returnsLocalCleanupCheckFailure() = runTest {
        var markCalled = false
        val result = runOAuthLaunchPreflight(
            isPendingPdfCleanup = { throw RuntimeException("boom") },
            clearPdfCache = { PdfCacheClearResult.Success },
            markPdfCleanupCompleted = { markCalled = true }
        )
        assertTrue("Expected Failed", result is OAuthLaunchPreflightResult.Failed)
        assertEquals(UiErrorReason.LOCAL_CLEANUP_CHECK_FAILED, (result as OAuthLaunchPreflightResult.Failed).reason)
        assertTrue("mark must not be called on ordinary exception", !markCalled)
    }

    @Test
    fun cancellationWhileCheckingMarker_isRethrownAsSameInstance() = runTest {
        val ce = CancellationException("test cancel")
        try {
            runOAuthLaunchPreflight(
                isPendingPdfCleanup = { throw ce },
                clearPdfCache = { PdfCacheClearResult.Success },
                markPdfCleanupCompleted = {}
            )
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertSame(ce, e)
        }
    }

    @Test
    fun cancellationDuringCleanup_isRethrownAsSameInstance() = runTest {
        var clearCounter = 0
        val ce = CancellationException("test cancel")
        try {
            runOAuthLaunchPreflight(
                isPendingPdfCleanup = { true },
                clearPdfCache = { clearCounter++; throw ce },
                markPdfCleanupCompleted = {}
            )
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertSame(ce, e)
            assertEquals(1, clearCounter)
        }
    }

    @Test
    fun cancellationWhileClearingMarker_isRethrownAsSameInstance() = runTest {
        val ce = CancellationException("test cancel")
        try {
            runOAuthLaunchPreflight(
                isPendingPdfCleanup = { true },
                clearPdfCache = { PdfCacheClearResult.Success },
                markPdfCleanupCompleted = { throw ce }
            )
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertSame(ce, e)
        }
    }
}
