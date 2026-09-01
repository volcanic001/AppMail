package com.david.mailapp.data.repository

import com.david.mailapp.data.remote.provider.BodyFetchResult

sealed interface EmailContentFetchOutcome {
    data class Persisted(val remote: BodyFetchResult) : EmailContentFetchOutcome
    data class MemoryOnly(val remote: BodyFetchResult, val cleanBody: String) : EmailContentFetchOutcome
}
