package com.david.mailapp.feature.trash

import com.david.mailapp.data.repository.EmailRepository
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.PaginatedResult
import kotlinx.coroutines.flow.Flow

interface TrashEmailSource {
    fun observeTrash(): Flow<List<Email>>
    suspend fun refreshTrash(pageToken: String?): PaginatedResult<Email>
    suspend fun deletePermanently(emailId: String)
    suspend fun restoreFromTrash(emailId: String)
}

internal class RepositoryTrashEmailSource(
    private val repository: EmailRepository
) : TrashEmailSource {
    override fun observeTrash() = repository.getTrash()
    override suspend fun refreshTrash(pageToken: String?) = repository.refreshTrash(pageToken)
    override suspend fun deletePermanently(emailId: String) = repository.deletePermanently(emailId)
    override suspend fun restoreFromTrash(emailId: String) = repository.restoreFromTrash(emailId)
}
