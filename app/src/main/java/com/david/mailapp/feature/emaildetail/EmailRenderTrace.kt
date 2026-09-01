package com.david.mailapp.feature.emaildetail

import android.os.SystemClock
import android.util.Log
import com.david.mailapp.BuildConfig

/** Debug-only, privacy-safe timeline for the email HTML rendering pipeline. */
internal object EmailRenderTrace {
    const val TAG = "MailRenderTrace"

    fun mailKey(emailId: String): String =
        com.david.mailapp.core.perf.MailOpenPerformanceTrace.mailKey(emailId)

    fun bodyKey(body: String?): String = body?.hashCode()?.toUInt()?.toString(16) ?: "none"

    fun now(): Long = SystemClock.elapsedRealtime()

    fun d(
        mail: String,
        layer: String,
        event: String,
        details: String = ""
    ) {
        if (!BuildConfig.DEBUG) return

        val suffix = if (details.isBlank()) "" else " $details"
        Log.d(
            TAG,
            "mail=$mail t=${SystemClock.elapsedRealtime()} " +
                "thread=${Thread.currentThread().name} layer=$layer event=$event$suffix"
        )
    }
}
