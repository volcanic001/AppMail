package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email

sealed interface EmailContentFetchOutcome {
    data class Persisted(val remote: Email) : EmailContentFetchOutcome
    data class MemoryOnly(val remote: Email, val cleanBody: String) : EmailContentFetchOutcome
}
