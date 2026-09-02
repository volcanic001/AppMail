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
    // 1. Content Unit
    val existingState = existing.contentState
    val incomingState = incoming.contentState

    val mergedBody: String
    val mergedCleanBody: String
    val mergedState: String
    val mergedKind: String
    val mergedRefs: String
    val mergedBytes: Long
    val mergedAccess: Long

    when {
        incomingState == "NOT_FETCHED" && (existingState == "READY" || existingState == "EMPTY") -> {
            mergedBody = existing.body
            mergedCleanBody = existing.cleanBody
            mergedState = existing.contentState
            mergedKind = existing.bodyKind
            mergedRefs = existing.inlineReferencesJson
            mergedBytes = existing.cachedContentBytes
            mergedAccess = existing.contentLastAccessEpochMs
        }
        incomingState == "READY" && existingState == "READY" -> {
            mergedBody = incoming.body
            mergedCleanBody = incoming.cleanBody
            mergedState = incoming.contentState
            mergedKind = incoming.bodyKind
            mergedRefs = incoming.inlineReferencesJson
            mergedBytes = incoming.cachedContentBytes
            mergedAccess = existing.contentLastAccessEpochMs
        }
        else -> {
            mergedBody = incoming.body
            mergedCleanBody = incoming.cleanBody
            mergedState = incoming.contentState
            mergedKind = incoming.bodyKind
            mergedRefs = incoming.inlineReferencesJson
            mergedBytes = incoming.cachedContentBytes
            mergedAccess = if (incomingState == "READY") incoming.contentLastAccessEpochMs else 0L
        }
    }

    val rfcMessageId = incoming.rfcMessageId ?: existing.rfcMessageId
    val rfcReferences = incoming.rfcReferences ?: existing.rfcReferences

    val (pdfJson, pdfScanned, hasAtt) = when {
        incoming.pdfMetadataScanned -> Triple(incoming.pdfAttachmentsJson, true, incoming.hasAttachments)
        existing.pdfMetadataScanned -> Triple(existing.pdfAttachmentsJson, true, existing.hasAttachments)
        else -> Triple(incoming.pdfAttachmentsJson, false, incoming.hasAttachments)
    }

    return incoming.copy(
        body = mergedBody,
        cleanBody = mergedCleanBody,
        contentState = mergedState,
        bodyKind = mergedKind,
        inlineReferencesJson = mergedRefs,
        cachedContentBytes = mergedBytes,
        contentLastAccessEpochMs = mergedAccess,
        pdfAttachmentsJson = pdfJson,
        pdfMetadataScanned = pdfScanned,
        hasAttachments = hasAtt,
        rfcMessageId = rfcMessageId,
        rfcReferences = rfcReferences
    )
}

@Dao
interface EmailDao {

    /** Observe lightweight summaries of emails in a folder, newest first. */
    @Query("""
        SELECT id, thread_id, sender, sender_initials, recipient_to, subject, snippet, timestamp, is_read, is_starred, has_attachments, labels, folder
        FROM emails
        WHERE folder = :folder
        ORDER BY timestamp DESC
    """)
    fun observeSummariesByFolder(folder: String): Flow<List<com.david.mailapp.data.local.entity.EmailSummaryProjection>>

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

    /** Persist an authoritative individual recovery and enforce the content budget atomically. */
    @Transaction
    suspend fun upsertRecoveredEmailAndEnforceBudget(
        entity: EmailEntity,
        maxBudgetBytes: Long
    ): EmailEntity {
        val existing = getByIdOnce(entity.id)
        val authoritative = entity.copy(
            rfcMessageId = entity.rfcMessageId ?: existing?.rfcMessageId,
            rfcReferences = entity.rfcReferences ?: existing?.rfcReferences,
            contentLastAccessEpochMs = if (entity.contentState == "READY") {
                existing?.contentLastAccessEpochMs ?: entity.contentLastAccessEpochMs
            } else {
                0L
            }
        )
        upsertAll(listOf(authoritative))

        if (authoritative.contentState == "READY") {
            var currentSum = sumReadyContentBytes() ?: 0L
            if (currentSum > maxBudgetBytes) {
                for (candidate in getLruEvictionCandidates(authoritative.id)) {
                    clearContent(candidate.id)
                    currentSum -= candidate.cachedContentBytes
                    if (currentSum <= maxBudgetBytes) break
                }
            }
        }
        return getByIdOnce(entity.id) ?: authoritative
    }

    /**
     * Upsert a batch of emails while preserving any previously downloaded
     * data (HTML bodies, PDF metadata).
     */
    @Transaction
    suspend fun upsertPreservingCachedContent(emails: List<EmailEntity>) {
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

    /**
     * Atomically persist the fetched body, cleaned body, and PDF metadata
     * for a single message. Replaces the separate [updateBody] and
     * [updateCleanBody] calls with one query.
     */
    @Query("""
        UPDATE emails SET
            body = :body,
            clean_body = :cleanBody,
            pdf_attachments_json = :pdfAttachmentsJson,
            pdf_metadata_scanned = 1,
            has_attachments = :hasAttachments,
            content_state = :contentState,
            body_kind = :bodyKind,
            inline_references_json = :inlineReferencesJson,
            cached_content_bytes = :cachedContentBytes
        WHERE id = :emailId
    """)
    suspend fun updateBodyAndPdfMetadata(
        emailId: String,
        body: String,
        cleanBody: String,
        pdfAttachmentsJson: String,
        hasAttachments: Boolean,
        contentState: String,
        bodyKind: String,
        inlineReferencesJson: String,
        cachedContentBytes: Long
    )

    @Query("""
        UPDATE emails SET
            clean_body = :cleanBody,
            cached_content_bytes = :cachedContentBytes
        WHERE id = :emailId
          AND body = :expectedRawBody
          AND content_state = 'READY'
          AND body_kind = 'HTML'
    """)
    suspend fun updateCleanBodyIfCurrent(
        emailId: String,
        expectedRawBody: String,
        cleanBody: String,
        cachedContentBytes: Long
    ): Int
    
    // LRU Policy Methods (Subfase 2.3)

    @Query("SELECT SUM(cached_content_bytes) FROM emails WHERE content_state = 'READY'")
    suspend fun sumReadyContentBytes(): Long?

    @Query("""
        SELECT * FROM emails 
        WHERE content_state = 'READY' AND id != :protectedEmailId
        ORDER BY content_last_access_epoch_ms ASC, id ASC
    """)
    suspend fun getLruEvictionCandidates(protectedEmailId: String): List<EmailEntity>

    @Query("""
        SELECT * FROM emails
        WHERE content_state = 'READY'
        ORDER BY content_last_access_epoch_ms ASC, id ASC
    """)
    suspend fun getGlobalLruEvictionCandidates(): List<EmailEntity>

    @Query("""
        UPDATE emails SET 
            body = '',
            clean_body = '',
            inline_references_json = '[]',
            cached_content_bytes = 0,
            content_state = 'NOT_FETCHED',
            body_kind = 'UNKNOWN',
            content_last_access_epoch_ms = 0
        WHERE id = :emailId
    """)
    suspend fun clearContent(emailId: String)



    @Transaction
    suspend fun applyLruAndSaveContent(
        emailId: String,
        body: String,
        cleanBody: String,
        pdfAttachmentsJson: String,
        hasAttachments: Boolean,
        contentState: String,
        bodyKind: String,
        inlineReferencesJson: String,
        cachedContentBytes: Long,
        maxBudgetBytes: Long
    ) {
        updateBodyAndPdfMetadata(
            emailId = emailId,
            body = body,
            cleanBody = cleanBody,
            pdfAttachmentsJson = pdfAttachmentsJson,
            hasAttachments = hasAttachments,
            contentState = contentState,
            bodyKind = bodyKind,
            inlineReferencesJson = inlineReferencesJson,
            cachedContentBytes = cachedContentBytes
        )

        var currentSum = sumReadyContentBytes() ?: 0L
        if (currentSum > maxBudgetBytes) {
            val candidates = getLruEvictionCandidates(emailId)
            for (candidate in candidates) {
                clearContent(candidate.id)
                currentSum -= candidate.cachedContentBytes
                if (currentSum <= maxBudgetBytes) break
            }
        }
    }

    @Transaction
    suspend fun enforceContentBudget(maxBudgetBytes: Long) {
        var currentSum = sumReadyContentBytes() ?: 0L
        if (currentSum <= maxBudgetBytes) return

        for (candidate in getGlobalLruEvictionCandidates()) {
            clearContent(candidate.id)
            currentSum -= candidate.cachedContentBytes
            if (currentSum <= maxBudgetBytes) break
        }
    }

    @Query("SELECT MAX(content_last_access_epoch_ms) FROM emails")
    suspend fun getMaxContentLastAccess(): Long?

    @Query("UPDATE emails SET content_last_access_epoch_ms = :newTimestamp WHERE id = :emailId AND content_state = 'READY'")
    suspend fun updateContentLastAccess(emailId: String, newTimestamp: Long)

    @Transaction
    suspend fun recordContentAccess(emailId: String) {
        val maxAccess = getMaxContentLastAccess() ?: 0L
        val newTimestamp = maxOf(System.currentTimeMillis(), maxAccess + 1)
        updateContentLastAccess(emailId, newTimestamp)
    }
}
