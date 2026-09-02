package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email

sealed interface EmailContentRecoveryResult {
    data class Found(
        val email: Email,
        val storage: EmailContentStorage
    ) : EmailContentRecoveryResult

    data object NotFound : EmailContentRecoveryResult
    data class Failure(val reason: EmailResolutionFailureReason) : EmailContentRecoveryResult
}

enum class EmailContentStorage {
    PERSISTED,
    MEMORY_ONLY
}
