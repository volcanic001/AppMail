package com.david.mailapp.feature.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test

class TrashDeleteConfirmationTest {

    @Ignore("Contrato pendiente: Fase 2.5")
    @Test
    fun `C9 solicitar borrado espera confirmacion antes de eliminar`() {
        var deleteCount = 0
        val coordinator = TrashDeleteCoordinator(
            deletePermanently = { deleteCount++ },
            restoreToInbox = {}
        )

        coordinator.requestDelete("email-1")

        assertEquals("Delete must wait for confirmation", 0, deleteCount)
        assertEquals("email-1", coordinator.pendingEmailId)

        coordinator.confirmDelete()
        assertEquals("Confirmation must delete exactly once", 1, deleteCount)
    }

    @Ignore("Contrato pendiente: Fase 2.5")
    @Test
    fun `C9 borrado permanente nunca ofrece undo`() {
        val coordinator = TrashDeleteCoordinator({}, {})
        assertFalse("Permanent deletion must not offer undo", coordinator.offersUndo)
    }
}
