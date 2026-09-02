package com.david.mailapp.data.remote.provider.gmail

enum class DiagnosticCategory {
    SUCCESS, IO, TRANSIENT_HTTP, PERMANENT_HTTP, NOT_FOUND, SESSION_EXPIRED, INVALID_RESPONSE, CANCELLED
}

data class NetworkDiagnosticEvent(
    val mailKey: String,
    val attempt: Int,
    val durationMs: Long,
    val category: DiagnosticCategory
)

typealias NetworkDiagnosticSink = (NetworkDiagnosticEvent) -> Unit
