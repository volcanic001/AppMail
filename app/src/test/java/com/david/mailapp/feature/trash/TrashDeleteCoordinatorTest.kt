package com.david.mailapp.feature.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashDeleteCoordinatorTest {

    @Test fun request_stores_id_does_not_call_back() {
        var calls = 0
        val c = TrashDeleteCoordinator()
        c.onConfirmed = { calls++ }
        c.requestDelete("e1")
        assertTrue(c.isDeletePending)
        assertEquals(0, calls)
    }

    @Test fun cancel_clears_id_without_calling_back() {
        var calls = 0
        val c = TrashDeleteCoordinator()
        c.onConfirmed = { calls++ }
        c.requestDelete("e1")
        c.cancelDelete()
        assertFalse(c.isDeletePending)
        assertEquals(0, calls)
    }

    @Test fun confirm_calls_back_once_and_clears_id() {
        var calls = 0
        val c = TrashDeleteCoordinator()
        c.onConfirmed = { calls++ }
        c.requestDelete("e1")
        c.confirmDelete()
        assertFalse(c.isDeletePending)
        assertEquals(1, calls)
    }

    @Test fun second_confirm_is_noop() {
        var calls = 0
        val c = TrashDeleteCoordinator()
        c.onConfirmed = { calls++ }
        c.requestDelete("e1")
        c.confirmDelete()
        c.confirmDelete()
        assertEquals(1, calls)
    }
}
