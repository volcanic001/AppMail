package com.david.mailapp.data.repository

import android.os.SystemClock

internal object RepositoryTrace {
    const val MAIL_PERF_TAG = "MailPerfTrace"
    const val RESOLVE_TAG = "EmailResolve"

    fun now(): Long = SystemClock.elapsedRealtime()
}
