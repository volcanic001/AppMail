package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email

/**
 * Typed outcome of [EmailRepository.resolveEmailById].
 *
 * Null is never used: a cache miss cannot be distinguished from a
 * connection error, and NotFound differs from a write failure.
 */
sealed class EmailResolutionResult {
    data class Found(val email: Email) : EmailResolutionResult()
    data object NotFound : EmailResolutionResult()
    data class Failure(val reason: EmailResolutionFailureReason) : EmailResolutionResult()
}

/** Why an email resolution failed. */
enum class EmailResolutionFailureReason {
    /** Blank or empty id — no Room or Gmail access. */
    INVALID_ID,

    /** No active write-guard session — resolution not possible. */
    NO_ACTIVE_ACCOUNT,

    /** Session was invalidated after the lease was captured. */
    SESSION_CHANGED,

    /** Network/IO failure after exhausting remote retries. */
    NO_CONNECTION,

    /** Session expired — reauthentication required. */
    SESSION_EXPIRED,

    /** Remote transient failure after exhausting retries. */
    TEMPORARY_REMOTE,

    /** Remote explicitly rejected the request (4xx other than 401/404). */
    REMOTE_REJECTED,

    /** Remote response was rejected or could not be interpreted. */
    INVALID_RESPONSE,

    /** Local Room read threw an unexpected exception. */
    LOCAL_READ_FAILED,

    /** Local Room write threw an unexpected exception. */
    LOCAL_WRITE_FAILED
}
