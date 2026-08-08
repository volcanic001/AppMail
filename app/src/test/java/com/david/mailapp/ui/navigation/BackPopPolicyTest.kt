package com.david.mailapp.ui.navigation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackPopPolicyTest {

    private val entryId = "entry-1"
    private val differentId = "entry-2"

    // ── Autorized ─────────────────────────────────────────────────

    @Test
    fun authorized_currentEntry_resumed_hasPrevious() {
        assertTrue(
            canPopBackFrom(entryId, entryId, Lifecycle.State.RESUMED, true)
        )
    }

    // ── Stale ID ──────────────────────────────────────────────────

    @Test
    fun blocked_staleId() {
        assertFalse(
            canPopBackFrom(entryId, differentId, Lifecycle.State.RESUMED, true)
        )
    }

    // ── Null current entry ────────────────────────────────────────

    @Test
    fun blocked_nullCurrentEntry() {
        assertFalse(
            canPopBackFrom(entryId, null, Lifecycle.State.RESUMED, true)
        )
    }

    // ── Lifecycle states ──────────────────────────────────────────

    @Test
    fun blocked_initialized() {
        assertFalse(
            canPopBackFrom(entryId, entryId, Lifecycle.State.INITIALIZED, true)
        )
    }

    @Test
    fun blocked_created() {
        assertFalse(
            canPopBackFrom(entryId, entryId, Lifecycle.State.CREATED, true)
        )
    }

    @Test
    fun blocked_started() {
        assertFalse(
            canPopBackFrom(entryId, entryId, Lifecycle.State.STARTED, true)
        )
    }

    @Test
    fun blocked_destroyed() {
        assertFalse(
            canPopBackFrom(entryId, entryId, Lifecycle.State.DESTROYED, true)
        )
    }

    // ── No previous entry ─────────────────────────────────────────

    @Test
    fun blocked_noPreviousEntry() {
        assertFalse(
            canPopBackFrom(entryId, entryId, Lifecycle.State.RESUMED, false)
        )
    }

    // ── Each condition blocks independently ───────────────────────

    @Test
    fun blocked_staleId_and_destroyed() {
        assertFalse(
            canPopBackFrom(entryId, differentId, Lifecycle.State.DESTROYED, true)
        )
    }

    @Test
    fun blocked_nullCurrent_and_destroyed() {
        assertFalse(
            canPopBackFrom(entryId, null, Lifecycle.State.DESTROYED, true)
        )
    }

    @Test
    fun blocked_allBadConditions() {
        assertFalse(
            canPopBackFrom(entryId, null, Lifecycle.State.DESTROYED, false)
        )
    }
}
