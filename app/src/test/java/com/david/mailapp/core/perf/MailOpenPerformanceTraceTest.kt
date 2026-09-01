package com.david.mailapp.core.perf

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailOpenPerformanceTraceTest {

    @Before
    fun setUp() {
        MailOpenPerformanceTrace.resetForTesting()
        MailOpenPerformanceTrace.isEnabled = true
    }

    @After
    fun tearDown() {
        MailOpenPerformanceTrace.resetForTesting()
        MailOpenPerformanceTrace.isEnabled = true
    }

    @Test
    fun mailKey_isDeterministicHexAndDoesNotLeakRawId() {
        val rawId1 = "18d1a2b3c4d5e6f7"
        val rawId2 = "user.sensitive+test@gmail.com"

        val key1 = MailOpenPerformanceTrace.mailKey(rawId1)
        val key2 = MailOpenPerformanceTrace.mailKey(rawId2)

        // Deterministic
        assertEquals(key1, MailOpenPerformanceTrace.mailKey(rawId1))
        assertNotEquals(key1, key2)

        // Does not contain raw email or ID
        assertFalse(key1.contains(rawId1))
        assertFalse(key2.contains("sensitive"))
        assertFalse(key2.contains("@"))

        // Blank returns "none"
        assertEquals("none", MailOpenPerformanceTrace.mailKey(""))
        assertEquals("none", MailOpenPerformanceTrace.mailKey("   "))
    }

    @Test
    fun sessionLifecycle_normalFlowCompletesSuccessfully() {
        val emailId = "msg_001"
        val sessionId = MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        assertTrue(sessionId > 0)

        // Trigger Ready
        MailOpenPerformanceTrace.onEmailReady(emailId)

        // Subsequent ready for different id does not fail
        MailOpenPerformanceTrace.onEmailReady("other_msg")
    }

    @Test
    fun sessionLifecycle_duplicateTapReplacesPreviousSession() {
        val emailId1 = "msg_001"
        val emailId2 = "msg_002"

        val session1 = MailOpenPerformanceTrace.onInboxItemClicked(emailId1)
        val session2 = MailOpenPerformanceTrace.onInboxItemClicked(emailId2)

        assertTrue(session2 > session1)

        // Ready on session 2
        MailOpenPerformanceTrace.onEmailReady(emailId2)
    }

    @Test
    fun sessionLifecycle_errorAndDisposeAbortSession() {
        val emailId = "msg_error"
        MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        MailOpenPerformanceTrace.onError(emailId, "timeout")

        val emailId2 = "msg_dispose"
        MailOpenPerformanceTrace.onInboxItemClicked(emailId2)
        MailOpenPerformanceTrace.onScreenDisposed(emailId2)
    }

    @Test
    fun traceSection_executesAndReturnsValue() {
        val emailId = "msg_trace"
        val result = MailOpenPerformanceTrace.traceSection(
            MailOpenPerformanceTrace.SECTION_RESOLVE,
            emailId
        ) {
            42
        }
        assertEquals(42, result)
    }

    @Test
    fun traceAsyncSection_executesAndReturnsValue() = runBlocking {
        val emailId = "msg_trace_async"
        val result = MailOpenPerformanceTrace.traceAsyncSection(
            MailOpenPerformanceTrace.SECTION_NETWORK_FULL,
            emailId
        ) {
            "network_response_ok"
        }
        assertEquals("network_response_ok", result)
    }

    @Test
    fun disabledTrace_isNoOp() {
        MailOpenPerformanceTrace.isEnabled = false

        val sessionId = MailOpenPerformanceTrace.onInboxItemClicked("msg_disabled")
        assertEquals(0, sessionId)

        MailOpenPerformanceTrace.onEmailReady("msg_disabled")
        MailOpenPerformanceTrace.onError("msg_disabled", "error")
        MailOpenPerformanceTrace.onScreenDisposed("msg_disabled")

        val cookie = MailOpenPerformanceTrace.beginSection(
            MailOpenPerformanceTrace.SECTION_RESOLVE,
            "msg_disabled"
        )
        assertEquals(0, cookie)
        MailOpenPerformanceTrace.endSection(MailOpenPerformanceTrace.SECTION_RESOLVE, "msg_disabled")
    }
}
