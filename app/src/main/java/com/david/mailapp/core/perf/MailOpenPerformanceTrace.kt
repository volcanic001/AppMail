package com.david.mailapp.core.perf

import android.os.SystemClock
import android.util.Log
import androidx.tracing.Trace
import com.david.mailapp.BuildConfig
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Privacy-safe, physically valid performance contract for tracking email open latency.
 *
 * Correlates the active email open session with monotonic session IDs and cookies.
 * All logging and tracing use exclusively the 16-hex truncated SHA-256 of the email ID (mailKey).
 */
internal object MailOpenPerformanceTrace {
    const val TAG = "MailOpenTrace"

    const val SECTION_TOTAL = "EmailOpen.Total"
    const val SECTION_RESOLVE = "EmailOpen.Resolve"
    const val SECTION_BODY_FETCH = "EmailOpen.BodyFetch"
    const val SECTION_HTML_BUILD = "EmailOpen.HtmlBuild"
    const val SECTION_WEBVIEW_VISUAL = "EmailOpen.WebViewVisual"
    const val SECTION_NETWORK_FULL = "EmailOpen.NetworkFull"

    /** Active capture identifier (UTC timestamp or run ID). */
    var captureId: String = "local"

    /** Controls whether tracing and logging are active. */
    var isEnabled: Boolean = BuildConfig.DEBUG || BuildConfig.PERF_TRACE_ENABLED

    private val sessionCounter = AtomicInteger(1)
    private val cookieCounter = AtomicInteger(1)

    data class ActiveSession(
        val sessionId: Int,
        val mailKey: String,
        val startTimeMs: Long,
        val totalCookie: Int,
        var networkFullCount: Int = 0,
        var networkFullDurationMs: Long = 0L,
        var isCompleted: Boolean = false,
        var isAborted: Boolean = false
    )

    @Volatile
    private var currentSession: ActiveSession? = null

    private val activeSectionCookies = ConcurrentHashMap<String, Int>()

    /**
     * Canonical mailKey: SHA-256 of emailId truncated to 16 hexadecimal characters.
     * Guaranteed deterministic, one-way, and never leaks raw Gmail IDs.
     */
    fun mailKey(emailId: String): String {
        if (emailId.isBlank()) return "none"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(emailId.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    fun getActiveSession(): ActiveSession? = currentSession

    /**
     * Triggered when a user taps an email in Inbox.
     * Starts [SECTION_TOTAL] and aborts any previous active session as replaced.
     */
    @Synchronized
    fun onInboxItemClicked(emailId: String): Int {
        if (!isEnabled) return 0

        val previous = currentSession
        if (previous != null && !previous.isCompleted && !previous.isAborted) {
            previous.isAborted = true
            endAsyncSectionInternal(SECTION_TOTAL, previous.mailKey, previous.totalCookie)
            val dur = now() - previous.startTimeMs
            Log.d(
                TAG,
                "[PERF_SESSION] captureId=$captureId sessionId=${previous.sessionId} mail=${previous.mailKey} " +
                    "section=$SECTION_TOTAL reason=replaced_by_new_tap durationMs=$dur outcome=ABORTED"
            )
        }

        val sessionId = sessionCounter.getAndIncrement()
        val totalCookie = cookieCounter.getAndIncrement()
        val key = mailKey(emailId)
        val startTime = now()

        currentSession = ActiveSession(
            sessionId = sessionId,
            mailKey = key,
            startTimeMs = startTime,
            totalCookie = totalCookie
        )

        beginAsyncSectionInternal(SECTION_TOTAL, key, totalCookie)
        Log.d(
            TAG,
            "[PERF_SESSION] captureId=$captureId sessionId=$sessionId mail=$key section=$SECTION_TOTAL event=START"
        )
        return sessionId
    }

    /**
     * Closes [SECTION_TOTAL] as COMPLETED when WebView visual state callback completes
     * and the loading overlay is fully dismissed.
     */
    @Synchronized
    fun onVisualReady(keyOrId: String) {
        if (!isEnabled) return

        val key = resolveKey(keyOrId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isCompleted && !session.isAborted) {
            session.isCompleted = true
            val dur = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.totalCookie)
            Log.d(
                TAG,
                "[PERF_SESSION] captureId=$captureId sessionId=${session.sessionId} mail=$key " +
                    "section=$SECTION_TOTAL durationMs=$dur outcome=COMPLETED"
            )
        }
    }

    /**
     * Aborts the session upon resolution or body error.
     */
    @Synchronized
    fun onError(keyOrId: String, reason: String) {
        if (!isEnabled) return

        val key = resolveKey(keyOrId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isCompleted && !session.isAborted) {
            session.isAborted = true
            val dur = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.totalCookie)
            Log.d(
                TAG,
                "[PERF_SESSION] captureId=$captureId sessionId=${session.sessionId} mail=$key " +
                    "section=$SECTION_TOTAL reason=$reason durationMs=$dur outcome=ABORTED"
            )
        }
    }

    /**
     * Aborts the session if screen is disposed before visual ready.
     */
    @Synchronized
    fun onScreenDisposed(keyOrId: String) {
        if (!isEnabled) return

        val key = resolveKey(keyOrId)
        val session = currentSession
        if (session != null && session.mailKey == key && !session.isCompleted && !session.isAborted) {
            session.isAborted = true
            val dur = now() - session.startTimeMs
            endAsyncSectionInternal(SECTION_TOTAL, key, session.totalCookie)
            Log.d(
                TAG,
                "[PERF_SESSION] captureId=$captureId sessionId=${session.sessionId} mail=$key " +
                    "section=$SECTION_TOTAL reason=screen_disposed durationMs=$dur outcome=ABORTED"
            )
        }
    }

    /**
     * Starts an async section with a monotonic cookie unique to the active session.
     */
    fun beginSection(section: String, keyOrId: String): Int {
        if (!isEnabled) return 0
        val key = resolveKey(keyOrId)
        val session = currentSession
        if (session == null || session.mailKey != key || session.isCompleted || session.isAborted) {
            return 0
        }
        val cookie = cookieCounter.getAndIncrement()
        val indexKey = "${session.sessionId}:$section"
        activeSectionCookies[indexKey] = cookie
        beginAsyncSectionInternal(section, key, cookie)
        return cookie
    }

    /**
     * Ends an async section for the active session.
     */
    fun endSection(section: String, keyOrId: String, cookie: Int? = null) {
        if (!isEnabled) return
        val key = resolveKey(keyOrId)
        val session = currentSession
        if (session == null || session.mailKey != key || session.isCompleted || session.isAborted) {
            return
        }
        val indexKey = "${session.sessionId}:$section"
        val resolvedCookie = cookie ?: activeSectionCookies.remove(indexKey) ?: return
        endAsyncSectionInternal(section, key, resolvedCookie)
    }

    inline fun <T> traceSection(section: String, keyOrId: String, block: () -> T): T {
        val key = resolveKey(keyOrId)
        val session = currentSession
        val isTarget = isEnabled && session != null && session.mailKey == key && !session.isCompleted && !session.isAborted
        if (!isTarget) return block()

        val cookie = cookieCounter.getAndIncrement()
        val t0 = now()
        beginAsyncSectionInternal(section, key, cookie)
        return try {
            block()
        } finally {
            val dur = now() - t0
            endAsyncSectionInternal(section, key, cookie)
            Log.d(
                TAG,
                "[TRACE_SECTION] captureId=$captureId sessionId=${session?.sessionId} mail=$key section=$section durationMs=$dur"
            )
        }
    }

    suspend inline fun <T> traceAsyncSection(
        section: String,
        keyOrId: String,
        crossinline block: suspend () -> T
    ): T {
        val key = resolveKey(keyOrId)
        val session = currentSession
        val isTarget = isEnabled && session != null && session.mailKey == key && !session.isCompleted && !session.isAborted
        if (!isTarget) return block()

        val cookie = cookieCounter.getAndIncrement()
        val t0 = now()
        beginAsyncSectionInternal(section, key, cookie)
        return try {
            block()
        } finally {
            val dur = now() - t0
            endAsyncSectionInternal(section, key, cookie)
            Log.d(
                TAG,
                "[TRACE_SECTION] captureId=$captureId sessionId=${session?.sessionId} mail=$key section=$section durationMs=$dur"
            )
        }
    }

    /**
     * Records an HTTP format=full request strictly if it belongs to the active opening session.
     */
    suspend inline fun <T> traceNetworkFull(
        emailId: String,
        crossinline block: suspend () -> T
    ): T {
        val key = mailKey(emailId)
        val session = currentSession
        val isTarget = isEnabled && session != null && session.mailKey == key && !session.isCompleted && !session.isAborted
        if (!isTarget) return block()

        val cookie = cookieCounter.getAndIncrement()
        val t0 = now()
        beginAsyncSectionInternal(SECTION_NETWORK_FULL, key, cookie)
        return try {
            block()
        } finally {
            val dur = now() - t0
            endAsyncSectionInternal(SECTION_NETWORK_FULL, key, cookie)
            synchronized(this) {
                if (session.mailKey == key && !session.isCompleted && !session.isAborted) {
                    session.networkFullCount++
                    session.networkFullDurationMs += dur
                    Log.d(
                        TAG,
                        "[TRACE_SECTION] captureId=$captureId sessionId=${session.sessionId} mail=$key section=$SECTION_NETWORK_FULL durationMs=$dur"
                    )
                }
            }
        }
    }

    @PublishedApi
    internal fun resolveKey(keyOrId: String): String =
        if (keyOrId.length == 16 && keyOrId.all { it.isDigit() || it in 'a'..'f' }) {
            keyOrId
        } else {
            mailKey(keyOrId)
        }

    @PublishedApi
    internal fun beginAsyncSectionInternal(section: String, mailKey: String, cookie: Int) {
        try {
            Trace.beginAsyncSection(section, cookie)
        } catch (_: Throwable) {}
    }

    @PublishedApi
    internal fun endAsyncSectionInternal(section: String, mailKey: String, cookie: Int) {
        try {
            Trace.endAsyncSection(section, cookie)
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun resetForTesting() {
        currentSession = null
        activeSectionCookies.clear()
        sessionCounter.set(1)
        cookieCounter.set(1)
        captureId = "local"
    }
}
