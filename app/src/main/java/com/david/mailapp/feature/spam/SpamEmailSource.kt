package com.david.mailapp.feature.spam

import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.flow.Flow

interface SpamEmailSource {
    fun observeSpam(): Flow<List<Email>>
    suspend fun refreshSpam(pageToken: String?): PaginatedResult<Email>
    /** "Not spam": moves the email back to the inbox. */
    suspend fun markNotSpam(emailId: String): EmailActionResult
    /** Deletes a spam email by moving it to the trash (user-chosen behaviour). */
    suspend fun deleteToTrash(emailId: String): EmailActionResult
}

internal class RepositorySpamEmailSource(
    private val repository: EmailRepository
) : SpamEmailSource {
    override fun observeSpam() = repository.getSpam()
    override suspend fun refreshSpam(pageToken: String?) = repository.refreshSpam(pageToken)
    override suspend fun markNotSpam(emailId: String) = repository.markNotSpam(emailId)
    override suspend fun deleteToTrash(emailId: String) = repository.moveToTrash(emailId)
}
