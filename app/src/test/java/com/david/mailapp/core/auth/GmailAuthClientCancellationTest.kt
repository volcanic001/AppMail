package com.david.mailapp.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailAuthClientCancellationTest {

    @Test
    fun successfulExchange_returnsSuccess() = runTest {
        var exchangeCalled = false
        val result = runOAuthTokenExchange {
            exchangeCalled = true
        }
        assertTrue("Expected Success", result is OAuthRedirectResult.Success)
        assertTrue("Exchange block must be called", exchangeCalled)
    }

    @Test
    fun ordinaryExchangeFailure_returnsTokenExchangeFailed() = runTest {
        var exchangeCalled = false
        val result = runOAuthTokenExchange {
            exchangeCalled = true
            throw RuntimeException("exchange failed")
        }
        assertTrue("Expected TokenExchangeFailed", result is OAuthRedirectResult.TokenExchangeFailed)
        assertTrue("Exchange block must be called", exchangeCalled)
    }

    @Test
    fun exchangeCancellation_isRethrownAsSameInstance() = runTest {
        val ce = CancellationException("test cancel")
        try {
            runOAuthTokenExchange {
                throw ce
            }
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertSame(ce, e)
        }
    }
}
