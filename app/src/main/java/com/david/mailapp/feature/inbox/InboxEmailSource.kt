package com.david.mailapp.feature.inbox

import com.david.mailapp.data.repository.EmailActionResult
import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.flow.Flow

interface InboxEmailSource {
    fun observeInbox(): Flow<List<Email>>
    suspend fun refreshInbox(pageToken: String?): PaginatedResult<Email>
    suspend fun moveToTrash(emailId: String): EmailActionResult
    suspend fun restoreFromTrash(emailId: String): EmailActionResult
    suspend fun markAsRead(emailId: String): EmailActionResult
}

internal class RepositoryInboxEmailSource(
    private val repository: EmailRepository
) : InboxEmailSource {
    override fun observeInbox() = repository.getInbox()
    override suspend fun refreshInbox(pageToken: String?) = repository.refreshInbox(pageToken)
    override suspend fun moveToTrash(emailId: String) = repository.moveToTrash(emailId)
    override suspend fun restoreFromTrash(emailId: String) = repository.restoreFromTrash(emailId)
    override suspend fun markAsRead(emailId: String) = repository.markAsRead(emailId)
}
