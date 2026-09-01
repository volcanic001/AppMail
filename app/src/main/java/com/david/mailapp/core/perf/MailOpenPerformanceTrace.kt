package com.david.mailapp.core.perf

import android.os.SystemClock
import android.util.Log
import androidx.tracing.Trace
import com.david.mailapp.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Internal, privacy-safe performance contract for tracking the critical email open path.
 *
 * Emits Android async trace sections (visible to Macrobenchmark via TraceSectionMetric)
 * and structured logcat records when enabled. No-op in pure release builds.
 */
internal object MailOpenPerformanceTrace {
    const val TAG = "MailOpenTrace"

    const val SECTION_TOTAL = "EmailOpen.Total"
    const val SECTION_RESOLVE = "EmailOpen.Resolve"
    const val SECTION_BODY_FETCH = "EmailOpen.BodyFetch"
    const val SECTION_HTML_BUILD = "EmailOpen.HtmlBuild"
    const val SECTION_WEBVIEW_VISUAL = "EmailOpen.WebViewVisual"
    const val SECTION_NETWORK_FULL = "EmailOpen.NetworkFull"

    /** Controls whether tracing and logging are active. Defaults to BuildConfig flags. */
    var isEnabled: Boolean = BuildConfig.DEBUG || BuildConfig.PERF_TRACE_ENABLED

    private val sessionCounter = AtomicInteger(1)

    /** Open session representation. */
    data class ActiveSession(
        val sessionId: Int,
        val mailKey: String,
        val startTimeMs: Long,
        var isReady: Boolean = false
    )

    @Volatile
    private var currentSession: ActiveSession? = null

    private val openSectionCookies = ConcurrentHashMap<String, Int>()

    /** Generates a privacy-safe truncated hexadecimal hash key for a given email id. */
    fun mailKey(emailId: String): String =
        if (emailId.isBlank()) "none" else emailId.hashCode().toUInt().toString(16)

    fun now(): Long = SystemClock.elapsedRealtime()

    /**
     * Called when a user taps an email item in Inbox list.
     * Replaces any existing uncompleted session and starts a new [SECTION_TOTAL] trace section.
     */
    @Synchronized
    fun onInboxItemClicked(emailId: String): Int {
        if (!isEnabled) return 0

        val previous = currentSession
        if (previous != null && !previous.isReady) {
            endAsyncSectionInternal(SECTION_TOTAL, previous.mailKey, previous.sessionId)
            Log.d(
                TAG,
                "[PERF_SESSION] REPLACED sessionId=${previous.sessionId} mail=${previous.mailKey} " +
                    "durationMs=${now() - previous.startTimeMs} outcome=REPLACED"
            )
        }

        val sessionId = sessionCounter.getAndIncrement()
        val key = mailKey(emailId)
        val startTime = now()
        currentSession = ActiveSession(sessionId = sessionId, mailKey = key, startTimeMs = startTime)

        beginAsyncSectionInternal(SECTION_TOTAL, key, sessionId)
        Log.d(
            TAG,
            "[PERF_SESSION] START sessionId=$sessionId mail=$key t=$startTime section=$SECTION_TOTAL"
        )
        return sessionId
    }

    /**
     * Called when email content reaches the visual ready state (first legible frame).
     */
    @Synchronized
    fun onEmailReady(emailId: String) {
        if (!isEnabled) return

        val key = mailKey(emailId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isReady) {
            session.isReady = true
            val duration = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.sessionId)
            Log.d(
                TAG,
                "[PERF_SESSION] READY sessionId=${session.sessionId} mail=$key " +
                    "durationMs=$duration outcome=COMPLETED"
            )
        }
    }

    /**
     * Called when an error occurs during resolution or body preparation.
     */
    @Synchronized
    fun onError(emailId: String, reason: String) {
        if (!isEnabled) return

        val key = mailKey(emailId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isReady) {
            session.isReady = true
            val duration = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.sessionId)
            Log.d(
                TAG,
                "[PERF_SESSION] ABORTED sessionId=${session.sessionId} mail=$key " +
                    "reason=$reason durationMs=$duration outcome=ABORTED"
            )
        }
    }

    /**
     * Called when EmailDetail screen is disposed. If the session has not reached ready, it is aborted.
     */
    @Synchronized
    fun onScreenDisposed(emailId: String) {
        if (!isEnabled) return

        val key = mailKey(emailId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isReady) {
            session.isReady = true
            val duration = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.sessionId)
            Log.d(
                TAG,
                "[PERF_SESSION] ABORTED sessionId=${session.sessionId} mail=$key " +
                    "reason=screen_disposed durationMs=$duration outcome=ABORTED"
            )
        }
    }

    /**
     * Starts an async trace section for a given section name and emailId.
     */
    fun beginSection(section: String, emailId: String): Int {
        if (!isEnabled) return 0
        val key = mailKey(emailId)
        val cookie = (key.hashCode() xor section.hashCode()).toInt()
        val sectionKey = "$section:$key"
        openSectionCookies[sectionKey] = cookie
        beginAsyncSectionInternal(section, key, cookie)
        return cookie
    }

    /**
     * Ends an async trace section for a given section name and emailId.
     */
    fun endSection(section: String, emailId: String, cookie: Int? = null) {
        if (!isEnabled) return
        val key = mailKey(emailId)
        val sectionKey = "$section:$key"
        val resolvedCookie = cookie ?: openSectionCookies.remove(sectionKey)
            ?: (key.hashCode() xor section.hashCode()).toInt()
        endAsyncSectionInternal(section, key, resolvedCookie)
    }

    inline fun <T> traceSection(section: String, emailId: String, block: () -> T): T {
        if (!isEnabled) return block()
        val key = mailKey(emailId)
        val cookie = (key.hashCode() xor section.hashCode()).toInt()
        val t0 = now()
        beginAsyncSectionInternal(section, key, cookie)
        return try {
            block()
        } finally {
            endAsyncSectionInternal(section, key, cookie)
            val duration = now() - t0
            Log.d(TAG, "[TRACE_SECTION] section=$section mail=$key durationMs=$duration")
        }
    }

    suspend inline fun <T> traceAsyncSection(
        section: String,
        emailId: String,
        crossinline block: suspend () -> T
    ): T {
        if (!isEnabled) return block()
        val key = mailKey(emailId)
        val cookie = (key.hashCode() xor section.hashCode()).toInt()
        val t0 = now()
        beginAsyncSectionInternal(section, key, cookie)
        return try {
            block()
        } finally {
            endAsyncSectionInternal(section, key, cookie)
            val duration = now() - t0
            Log.d(TAG, "[TRACE_SECTION] section=$section mail=$key durationMs=$duration")
        }
    }

    @PublishedApi
    internal fun beginAsyncSectionInternal(section: String, mailKey: String, cookie: Int) {
        try {
            Trace.beginAsyncSection(section, cookie)
        } catch (_: Throwable) {}
        Log.d(TAG, "[TRACE_START] section=$section mail=$mailKey cookie=$cookie t=${now()}")
    }

    @PublishedApi
    internal fun endAsyncSectionInternal(section: String, mailKey: String, cookie: Int) {
        try {
            Trace.endAsyncSection(section, cookie)
        } catch (_: Throwable) {}
        Log.d(TAG, "[TRACE_END] section=$section mail=$mailKey cookie=$cookie t=${now()}")
    }

    /** Reset state (primarily for tests). */
    @Synchronized
    fun resetForTesting() {
        currentSession = null
        openSectionCookies.clear()
        sessionCounter.set(1)
    }
}
