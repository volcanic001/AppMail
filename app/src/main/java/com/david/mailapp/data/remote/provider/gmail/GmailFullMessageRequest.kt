package com.david.mailapp.data.remote.provider.gmail

import com.david.mailapp.core.perf.MailOpenPerformanceTrace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

/** The single Gmail route used to retrieve a complete message. */
internal suspend fun requestFullMessage(
    client: HttpClient,
    messageId: String
): HttpResponse = MailOpenPerformanceTrace.traceNetworkFull(messageId) {
    client.get("users/me/messages/$messageId") {
        parameter("format", "full")
        parameter("fields", GmailProjections.FULL_MESSAGE_FIELDS)
    }
}
