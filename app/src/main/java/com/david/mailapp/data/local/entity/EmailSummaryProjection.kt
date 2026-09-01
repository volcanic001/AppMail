package com.david.mailapp.data.local.entity

import androidx.room.ColumnInfo

/**
 * Lightweight projection of [EmailEntity] for list views.
 * Excludes heavy fields like body, clean_body, pdf metadata, and rfc headers.
 */
data class EmailSummaryProjection(
    val id: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "sender") val from: String,
    @ColumnInfo(name = "sender_initials") val fromInitials: String,
    @ColumnInfo(name = "recipient_to") val to: String,
    val subject: String,
    val snippet: String,
    val timestamp: Long,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "is_starred") val isStarred: Boolean,
    @ColumnInfo(name = "has_attachments") val hasAttachments: Boolean,
    val labels: String,
    val folder: String
)
