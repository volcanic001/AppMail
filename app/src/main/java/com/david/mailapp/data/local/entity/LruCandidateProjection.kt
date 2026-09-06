package com.david.mailapp.data.local.entity

import androidx.room.ColumnInfo

/**
 * Lightweight projection of [EmailEntity] for LRU eviction.
 * Excludes heavy fields to minimize memory and I/O usage when deciding what to evict.
 */
data class LruCandidateProjection(
    val id: String,
    @ColumnInfo(name = "cached_content_bytes") val cachedContentBytes: Long,
    @ColumnInfo(name = "content_last_access_epoch_ms") val contentLastAccessEpochMs: Long
)
