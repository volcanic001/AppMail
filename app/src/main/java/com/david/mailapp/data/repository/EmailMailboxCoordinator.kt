package com.david.mailapp.data.repository

import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.domain.model.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Live Room-backed reads for the mailbox folders. Delegates only to the DAO;
 * no provider, cache, dispatchers or session state involved.
 */
internal class EmailMailboxCoordinator(
    private val dao: EmailDao
) {
    fun getInbox(): Flow<List<Email>> {
        return dao.getByFolder("inbox").map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTrash(): Flow<List<Email>> {
        return dao.getByFolder("trash").map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getEmailById(emailId: String): Flow<Email?> {
        return dao.getById(emailId).map { entity -> entity?.toDomain() }
    }
}
