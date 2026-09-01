package com.david.mailapp.core.perf

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun mailKey_isDeterministic16HexCharactersAndNeverLeaksRawId() {
        val rawId1 = "18d1a2b3c4d5e6f7"
        val rawId2 = "sensitive.user+token123@gmail.com"

        val key1 = MailOpenPerformanceTrace.mailKey(rawId1)
        val key2 = MailOpenPerformanceTrace.mailKey(rawId2)

        // Must be exactly 16 hex characters
        assertEquals(16, key1.length)
        assertEquals(16, key2.length)
        assertTrue(key1.all { it.isDigit() || it in 'a'..'f' })
        assertTrue(key2.all { it.isDigit() || it in 'a'..'f' })

        // Same ID produces the same mailKey
        assertEquals(key1, MailOpenPerformanceTrace.mailKey(rawId1))
        assertNotEquals(key1, key2)

        // Raw ID, tokens and emails are never leaked in key
        assertFalse(key1.contains(rawId1))
        assertFalse(key2.contains("sensitive"))
        assertFalse(key2.contains("token"))
        assertFalse(key2.contains("@"))

        // Blank returns "none"
        assertEquals("none", MailOpenPerformanceTrace.mailKey(""))
        assertEquals("none", MailOpenPerformanceTrace.mailKey("   "))
    }

    @Test
    fun foreignNetworkRequest_duringActiveSession_doesNotIncrementNetworkFull() = runBlocking {
        val activeEmailId = "active_email_123"
        val foreignEmailId = "foreign_background_sync_456"

        MailOpenPerformanceTrace.onInboxItemClicked(activeEmailId)
        val session = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session)
        assertEquals(0, session!!.networkFullCount)

        // Execute network request for a FOREIGN email
        val foreignResult = MailOpenPerformanceTrace.traceNetworkFull(foreignEmailId) {
            "foreign_body_downloaded"
        }
        assertEquals("foreign_body_downloaded", foreignResult)

        // NetworkFullCount for active session must STILL be 0
        assertEquals(0, session.networkFullCount)

        // Now execute network request for the ACTIVE email
        val activeResult = MailOpenPerformanceTrace.traceNetworkFull(activeEmailId) {
            "active_body_downloaded"
        }
        assertEquals("active_body_downloaded", activeResult)

        // NetworkFullCount for active session must now be 1
        assertEquals(1, session.networkFullCount)
    }

    @Test
    fun sessionLifecycle_visualReadyClosesSessionAndIgnoresDuplicateCallbacks() {
        val emailId = "target_msg_789"
        val mailKey = MailOpenPerformanceTrace.mailKey(emailId)

        MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        val session = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session)
        assertFalse(session!!.isCompleted)
        assertFalse(session.isAborted)

        // Visual ready callback completes session
        MailOpenPerformanceTrace.onVisualReady(mailKey)
        assertTrue(session.isCompleted)
        assertFalse(session.isAborted)

        // Duplicate visual callback is safely ignored and does not throw or double complete
        MailOpenPerformanceTrace.onVisualReady(mailKey)
        assertTrue(session.isCompleted)
    }

    @Test
    fun sessionLifecycle_errorAbortsSession() {
        val emailId = "error_msg_001"
        val mailKey = MailOpenPerformanceTrace.mailKey(emailId)

        MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        val session = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session)

        MailOpenPerformanceTrace.onError(mailKey, "resolution_error_NOT_FOUND")
        assertTrue(session!!.isAborted)
        assertFalse(session.isCompleted)

        // Subsequent ready after abort is ignored
        MailOpenPerformanceTrace.onVisualReady(mailKey)
        assertFalse(session.isCompleted)
    }

    @Test
    fun sessionLifecycle_screenDisposedBeforeVisualReadyAbortsSession() {
        val emailId = "back_pressed_msg"
        val mailKey = MailOpenPerformanceTrace.mailKey(emailId)

        MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        val session = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session)

        // User navigated back or screen disposed before visual callback
        MailOpenPerformanceTrace.onScreenDisposed(mailKey)
        assertTrue(session!!.isAborted)
        assertFalse(session.isCompleted)
    }

    @Test
    fun sessionLifecycle_duplicateTapAbortsPreviousSessionAsReplaced() {
        val emailId1 = "email_first"
        val emailId2 = "email_second"

        val s1Id = MailOpenPerformanceTrace.onInboxItemClicked(emailId1)
        val session1 = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session1)
        assertEquals(s1Id, session1!!.sessionId)

        // User taps another email before first one finished loading
        val s2Id = MailOpenPerformanceTrace.onInboxItemClicked(emailId2)
        val session2 = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session2)

        assertTrue(session1.isAborted)
        assertFalse(session1.isCompleted)
        assertEquals(s2Id, session2!!.sessionId)
        assertNotEquals(s1Id, s2Id)
    }

    @Test
    fun activeSession_allSectionsShareSessionIdAndMailKey() = runBlocking {
        val emailId = "coherent_session_msg"
        val mailKey = MailOpenPerformanceTrace.mailKey(emailId)

        val sId = MailOpenPerformanceTrace.onInboxItemClicked(emailId)
        val session = MailOpenPerformanceTrace.getActiveSession()
        assertNotNull(session)
        assertEquals(sId, session!!.sessionId)
        assertEquals(mailKey, session.mailKey)

        // Resolve section
        val resolveRes = MailOpenPerformanceTrace.traceAsyncSection(
            MailOpenPerformanceTrace.SECTION_RESOLVE,
            mailKey
        ) {
            "resolved"
        }
        assertEquals("resolved", resolveRes)

        // Body fetch section
        val bodyRes = MailOpenPerformanceTrace.traceAsyncSection(
            MailOpenPerformanceTrace.SECTION_BODY_FETCH,
            mailKey
        ) {
            "body"
        }
        assertEquals("body", bodyRes)

        // Html build section
        val htmlRes = MailOpenPerformanceTrace.traceSection(
            MailOpenPerformanceTrace.SECTION_HTML_BUILD,
            mailKey
        ) {
            "clean_html"
        }
        assertEquals("clean_html", htmlRes)

        // WebView visual section
        val cookie = MailOpenPerformanceTrace.beginSection(
            MailOpenPerformanceTrace.SECTION_WEBVIEW_VISUAL,
            mailKey
        )
        assertTrue(cookie > 0)
        MailOpenPerformanceTrace.endSection(
            MailOpenPerformanceTrace.SECTION_WEBVIEW_VISUAL,
            mailKey,
            cookie
        )

        // NetworkFull section
        MailOpenPerformanceTrace.traceNetworkFull(emailId) {
            "full_payload"
        }
        assertEquals(1, session.networkFullCount)

        // Visual ready completes session
        MailOpenPerformanceTrace.onVisualReady(mailKey)
        assertTrue(session.isCompleted)
        assertFalse(session.isAborted)
    }

    @Test
    fun disabledTrace_isCompletelyNoOp() = runBlocking {
        MailOpenPerformanceTrace.isEnabled = false

        val sessionId = MailOpenPerformanceTrace.onInboxItemClicked("msg_disabled")
        assertEquals(0, sessionId)
        assertNull(MailOpenPerformanceTrace.getActiveSession())

        MailOpenPerformanceTrace.onVisualReady("msg_disabled")
        MailOpenPerformanceTrace.onError("msg_disabled", "err")
        MailOpenPerformanceTrace.onScreenDisposed("msg_disabled")

        val cookie = MailOpenPerformanceTrace.beginSection(
            MailOpenPerformanceTrace.SECTION_RESOLVE,
            "msg_disabled"
        )
        assertEquals(0, cookie)
        MailOpenPerformanceTrace.endSection(MailOpenPerformanceTrace.SECTION_RESOLVE, "msg_disabled")

        val netRes = MailOpenPerformanceTrace.traceNetworkFull("msg_disabled") { "ok" }
        assertEquals("ok", netRes)
    }
}
