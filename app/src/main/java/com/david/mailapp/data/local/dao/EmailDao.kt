package com.david.mailapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.david.mailapp.data.local.entity.EmailEntity
import kotlinx.coroutines.flow.Flow

/**
 * Merges an incoming [incoming] entity with the [existing] entity stored in Room.
 *
 * This function preserves previously-fetched data (body, cleanBody, PDF metadata,
 * RFC headers) when the incoming sync is less detailed.
 *
 * Rules:
 * - body and cleanBody: preserve existing when the incoming value is blank.
 * - PDF metadata (json, scanned flag, hasAttachments):
 *   - If incoming.pdfMetadataScanned == true → incoming metadata is authoritative.
 *   - If incoming.pdfMetadataScanned == false and existing.pdfMetadataScanned == true
 *     → preserve existing PDF metadata.
 *   - If both are unscanned → use incoming (noop — both have defaults).
 */
internal fun mergeWithExisting(incoming: EmailEntity, existing: EmailEntity): EmailEntity {
    val body = if (incoming.body.isBlank() && existing.body.isNotBlank()) existing.body else incoming.body
    val cleanBody = if (incoming.cleanBody.isBlank() && existing.cleanBody.isNotBlank()) existing.cleanBody else incoming.cleanBody
    val rfcMessageId = incoming.rfcMessageId ?: existing.rfcMessageId
    val rfcReferences = incoming.rfcReferences ?: existing.rfcReferences

    val (pdfJson, pdfScanned, hasAtt) = when {
        incoming.pdfMetadataScanned -> Triple(incoming.pdfAttachmentsJson, true, incoming.hasAttachments)
        existing.pdfMetadataScanned -> Triple(existing.pdfAttachmentsJson, true, existing.hasAttachments)
        else -> Triple(incoming.pdfAttachmentsJson, false, incoming.hasAttachments)
    }

    return incoming.copy(
        body = body,
        cleanBody = cleanBody,
        pdfAttachmentsJson = pdfJson,
        pdfMetadataScanned = pdfScanned,
        hasAttachments = hasAtt,
        rfcMessageId = rfcMessageId,
        rfcReferences = rfcReferences
    )
}

@Dao
interface EmailDao {

    /** Observe all emails in a folder (inbox/trash), newest first. */
    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY timestamp DESC")
    fun getByFolder(folder: String): Flow<List<EmailEntity>>

    /** Get a single email by ID. */
    @Query("SELECT * FROM emails WHERE id = :emailId LIMIT 1")
    fun getById(emailId: String): Flow<EmailEntity?>

    /** Suspendable single-point read for resolution and merge operations. */
    @Query("SELECT * FROM emails WHERE id = :emailId LIMIT 1")
    suspend fun getByIdOnce(emailId: String): EmailEntity?

    /** Insert or update a batch of emails (from remote sync). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(emails: List<EmailEntity>)

    /** Move an email to a different folder. */
    @Query("UPDATE emails SET folder = :newFolder WHERE id = :emailId")
    suspend fun moveToFolder(emailId: String, newFolder: String)

    /** Mark as read/unread. */
    @Query("UPDATE emails SET is_read = :isRead WHERE id = :emailId")
    suspend fun updateReadStatus(emailId: String, isRead: Boolean)

    /** Permanent delete from local cache. */
    @Query("DELETE FROM emails WHERE id = :emailId")
    suspend fun deleteById(emailId: String)

    /** Clear all emails in a folder (before full refresh). */
    @Query("DELETE FROM emails WHERE folder = :folder")
    suspend fun clearFolder(folder: String)

    @Query("SELECT * FROM emails WHERE folder = :folder")
    suspend fun getEntitiesByFolderSync(folder: String): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE id IN (:ids)")
    suspend fun getEntitiesByIdsSync(ids: List<String>): List<EmailEntity>

    /**
     * Replace the cached first page for a folder as one atomic operation,
     * while preserving any previously downloaded data (HTML bodies, PDF metadata,
     * RFC headers) — even when the same ID already exists in a different folder
     * (e.g. Other → Inbox / Other → Trash transitions).
     */
    @Transaction
    suspend fun replaceFolder(folder: String, emails: List<EmailEntity>) {
        val existingInFolder = getEntitiesByFolderSync(folder)
        val existingById = existingInFolder.associateBy { it.id }.toMutableMap()

        // Also look up by incoming IDs to cover cross-folder transitions
        val incomingIds = emails.map { it.id }
        val existingByIncomingId = getEntitiesByIdsSync(incomingIds).associateBy { it.id }
        for ((id, entity) in existingByIncomingId) {
            existingById.putIfAbsent(id, entity)
        }

        clearFolder(folder)

        val preservedEmails = emails.map { entity ->
            val existing = existingById[entity.id]
            if (existing != null) mergeWithExisting(entity, existing) else entity
        }
        upsertAll(preservedEmails)
    }

    /**
     * Upsert a single entity, merging with any existing row to preserve
     * body, cleanBody, PDF metadata, and RFC headers. Returns the merged
     * entity as persisted in Room.
     */
    @Transaction
    suspend fun upsertWithMerge(entity: EmailEntity): EmailEntity {
        val existing = getByIdOnce(entity.id)
        val merged = if (existing != null) mergeWithExisting(entity, existing) else entity
        upsertAll(listOf(merged))
        return getByIdOnce(entity.id) ?: merged
    }

    /**
     * Upsert a batch of emails while preserving any previously downloaded
     * data (HTML bodies, PDF metadata).
     */
    @Transaction
    suspend fun upsertPreservingBodies(emails: List<EmailEntity>) {
        if (emails.isEmpty()) return
        val preserved = emails.chunked(500).flatMap { chunk ->
            val existing = getEntitiesByIdsSync(chunk.map { it.id }).associateBy { it.id }
            chunk.map { entity ->
                val existingEntity = existing[entity.id]
                if (existingEntity != null) mergeWithExisting(entity, existingEntity) else entity
            }
        }
        upsertAll(preserved)
    }

    /** Persist the fetched HTML body and clean body. */
    @Query("UPDATE emails SET body = :body WHERE id = :emailId")
    suspend fun updateBody(emailId: String, body: String)

    /** Persist the Jsoup-cleaned HTML body for a message. */
    @Query("UPDATE emails SET clean_body = :cleanBody WHERE id = :emailId")
    suspend fun updateCleanBody(emailId: String, cleanBody: String)

    /**
     * Atomically persist the fetched body, cleaned body, and PDF metadata
     * for a single message. Replaces the separate [updateBody] and
     * [updateCleanBody] calls with one query.
     */
    @Query("""
        UPDATE emails SET
            body = CASE WHEN :body != '' THEN :body ELSE body END,
            clean_body = CASE WHEN :cleanBody != '' THEN :cleanBody ELSE clean_body END,
            pdf_attachments_json = :pdfAttachmentsJson,
            pdf_metadata_scanned = 1,
            has_attachments = :hasAttachments
        WHERE id = :emailId
    """)
    suspend fun updateBodyAndPdfMetadata(
        emailId: String,
        body: String,
        cleanBody: String,
        pdfAttachmentsJson: String,
        hasAttachments: Boolean
    )
}
