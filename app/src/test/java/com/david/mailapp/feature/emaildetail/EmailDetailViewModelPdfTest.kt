package com.david.mailapp.feature.emaildetail

import com.david.mailapp.data.pdf.PdfDownloadFailure
import com.david.mailapp.data.pdf.PdfDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the ViewModel's PDF download state machine
 * — [EmailDetailViewModel.onPdfAttachmentClick] and
 *   [EmailDetailViewModel.onPdfSaveClick] decision logic.
 *
 * Sigue el mismo patrón que [EmailDetailViewModelTest]:
 * no instancia el ViewModel completo (requiere Room),
 * sino que prueba la lógica de decisión como función pura.
 */
class EmailDetailViewModelPdfTest {

    // ── Decision function (misma lógica que onPdfAttachmentClick) ──

    private fun shouldStartDownload(currentState: PdfDownloadState?): Boolean =
        when (currentState) {
            null, is PdfDownloadState.Idle, is PdfDownloadState.Error -> true
            PdfDownloadState.Downloading, is PdfDownloadState.Ready -> false
        }

    // ── Tests: click en cada estado ──────────────────────────────

    @Test
    fun `click when state is null starts download`() {
        assertTrue("null state should start download", shouldStartDownload(null))
    }

    @Test
    fun `click on Idle starts download`() {
        assertTrue("Idle should start download", shouldStartDownload(PdfDownloadState.Idle))
    }

    @Test
    fun `click on Error starts download (retry)`() {
        assertTrue("Error should start download", shouldStartDownload(
            PdfDownloadState.Error(PdfDownloadFailure.NETWORK)
        ))
        assertTrue("TOO_LARGE error should allow retry", shouldStartDownload(
            PdfDownloadState.Error(PdfDownloadFailure.TOO_LARGE)
        ))
        assertTrue("INVALID_PDF error should allow retry", shouldStartDownload(
            PdfDownloadState.Error(PdfDownloadFailure.INVALID_PDF)
        ))
    }

    @Test
    fun `click on Downloading does nothing`() {
        assertFalse("Downloading should NOT start download",
            shouldStartDownload(PdfDownloadState.Downloading))
    }

    @Test
    fun `click on Ready does nothing`() {
        assertFalse("Ready(100) should NOT start download",
            shouldStartDownload(PdfDownloadState.Ready(100L)))
        assertFalse("Ready(0) should NOT start download",
            shouldStartDownload(PdfDownloadState.Ready(0L)))
    }

    // ── State map transitions ────────────────────────────────────

    @Test
    fun `state transition null to Downloading then Ready`() {
        val attachmentId = "att_abc"
        val states = mutableMapOf<String, PdfDownloadState>()

        // Before any action: no state
        assertTrue(shouldStartDownload(states[attachmentId]))

        // Click → Downloading
        states[attachmentId] = PdfDownloadState.Downloading
        assertEquals(PdfDownloadState.Downloading, states[attachmentId])
        assertFalse(shouldStartDownload(states[attachmentId])) // second click no-op

        // Download completes → Ready
        states[attachmentId] = PdfDownloadState.Ready(4096L)
        assertEquals(PdfDownloadState.Ready(4096L), states[attachmentId])
        assertFalse(shouldStartDownload(states[attachmentId])) // third click no-op
    }

    @Test
    fun `state transition Idle to Downloading to Error then retry to Downloading`() {
        val attachmentId = "att_xyz"
        val states = mutableMapOf<String, PdfDownloadState>()

        // Initial
        states[attachmentId] = PdfDownloadState.Idle
        assertEquals(PdfDownloadState.Idle, states[attachmentId])
        assertTrue(shouldStartDownload(states[attachmentId]))

        // Click → Downloading
        states[attachmentId] = PdfDownloadState.Downloading
        assertFalse(shouldStartDownload(states[attachmentId])) // no-op

        // Fail → Error
        states[attachmentId] = PdfDownloadState.Error(PdfDownloadFailure.NETWORK)
        assertTrue(shouldStartDownload(states[attachmentId])) // retry allowed

        // Retry click → Downloading
        states[attachmentId] = PdfDownloadState.Downloading
        assertEquals(PdfDownloadState.Downloading, states[attachmentId])
        assertFalse(shouldStartDownload(states[attachmentId])) // no-op

        // Retry succeeds → Ready
        states[attachmentId] = PdfDownloadState.Ready(8192L)
        assertFalse(shouldStartDownload(states[attachmentId])) // no-op
    }

    // ── Multiple attachments ─────────────────────────────────────

    @Test
    fun `multiple attachments have independent states`() {
        val states = mutableMapOf<String, PdfDownloadState>()

        states["att_a"] = PdfDownloadState.Downloading
        states["att_b"] = PdfDownloadState.Idle
        states["att_c"] = PdfDownloadState.Ready(100L)

        assertFalse("att_a: Downloading → no-op", shouldStartDownload(states["att_a"]))
        assertTrue("att_b: Idle → start", shouldStartDownload(states["att_b"]))
        assertFalse("att_c: Ready → no-op", shouldStartDownload(states["att_c"]))
        assertTrue("att_d (uninitialized) → start", shouldStartDownload(states["att_d"]))
    }

    // ── Edge cases ───────────────────────────────────────────────

    @Test
    fun `empty states map handles all keys as not started`() {
        val states = emptyMap<String, PdfDownloadState>()
        assertTrue("any uninitialized key should start download",
            shouldStartDownload(states["anything"]))
    }

    @Test
    fun `Downloading state prevents duplicate download`() {
        val attachmentId = "att_unique"
        val states = mutableMapOf<String, PdfDownloadState>()

        // First click
        states[attachmentId] = PdfDownloadState.Downloading
        assertFalse("second click while Downloading must be no-op",
            shouldStartDownload(states[attachmentId]))

        // Verificar que Downloading se mantiene (no se sobrescribe)
        assertEquals(PdfDownloadState.Downloading, states[attachmentId])
    }

    @Test
    fun `Ready state after cache check persists`() {
        val attachmentId = "att_cached"
        val states = mutableMapOf<String, PdfDownloadState>()

        // Cache restore (simulado desde collect block)
        states[attachmentId] = PdfDownloadState.Ready(26214400L) // 25 MiB

        assertEquals(PdfDownloadState.Ready(26214400L), states[attachmentId])
        assertFalse("Ready should never re-download",
            shouldStartDownload(states[attachmentId]))
    }

    // ── Auto-open decision logic (Fase 5 onPdfAttachmentClick) ──

    private enum class ClickAction { SET_PENDING_AND_DOWNLOAD, EMIT_EVENT, NO_OP }

    /**
     * Misma lógica que [EmailDetailViewModel.onPdfAttachmentClick].
     *
     * - Ready        → EMIT_EVENT
     * - Idle / Error → SET_PENDING_AND_DOWNLOAD
     * - Downloading  → NO_OP
     */
    private fun clickAction(currentState: PdfDownloadState?): ClickAction = when (currentState) {
        null, is PdfDownloadState.Idle, is PdfDownloadState.Error -> ClickAction.SET_PENDING_AND_DOWNLOAD
        is PdfDownloadState.Ready -> ClickAction.EMIT_EVENT
        PdfDownloadState.Downloading -> ClickAction.NO_OP
    }

    @Test
    fun `click on Ready emits open event`() {
        assertEquals("Ready must emit event",
            ClickAction.EMIT_EVENT, clickAction(PdfDownloadState.Ready(4096L)))
    }

    @Test
    fun `click on Idle sets pending and downloads`() {
        assertEquals("Idle must set pending and download",
            ClickAction.SET_PENDING_AND_DOWNLOAD, clickAction(PdfDownloadState.Idle))
    }

    @Test
    fun `click on Error sets pending and downloads (retry)`() {
        val errors = PdfDownloadFailure.values().map {
            PdfDownloadState.Error(it)
        }
        errors.forEach { errorState ->
            assertEquals("Error(${errorState.reason}) must retry",
                ClickAction.SET_PENDING_AND_DOWNLOAD, clickAction(errorState))
        }
    }

    @Test
    fun `click on null (uninitialized) sets pending and downloads`() {
        assertEquals("null state must start download",
            ClickAction.SET_PENDING_AND_DOWNLOAD, clickAction(null))
    }

    // ── Multiple attachments auto-open priority ──────────────────

    @Test
    fun `only last clicked auto-opens when multiple PDFs are clicked`() {
        // Simula: click en att_a (Idle→Downloading), click en att_b (Ready)
        // att_b debe emitir, att_a no debe emitir al terminar

        val stableIds = listOf("att_a", "att_b")
        val states = mutableMapOf<String, PdfDownloadState>()
        states["att_a"] = PdfDownloadState.Idle
        states["att_b"] = PdfDownloadState.Ready(100L)

        var pendingStableId: String? = null
        val emittedEvents = mutableListOf<String>()

        // Click on att_a → pending + download
        val actionA = clickAction(states["att_a"])
        assertEquals(ClickAction.SET_PENDING_AND_DOWNLOAD, actionA)
        pendingStableId = "att_a"

        // Click on att_b → emit immediately (overwrites pending)
        val actionB = clickAction(states["att_b"])
        assertEquals(ClickAction.EMIT_EVENT, actionB)
        emittedEvents.add("att_b")
        pendingStableId = null  // se cancela la pendiente anterior

        // att_a termina su descarga exitosamente
        states["att_a"] = PdfDownloadState.Ready(200L)
        // Pero pendingStableId es null, no debe emitir
        if (pendingStableId == "att_a") {
            emittedEvents.add("att_a")
        }

        assertEquals("only att_b must have been emitted",
            listOf("att_b"), emittedEvents)
    }

    @Test
    fun `click on Idle then different Idle only last auto-opens`() {
        val states = mutableMapOf<String, PdfDownloadState>()
        states["first"] = PdfDownloadState.Idle
        states["second"] = PdfDownloadState.Idle

        val emittedEvents = mutableListOf<String>()
        var pendingRequest: PdfExternalActionRequest? = null

        // Click on first
        pendingRequest = PdfExternalActionRequest.Open("", "first", "a.pdf")
        states["first"] = PdfDownloadState.Downloading

        // Click on second (overwrites pending)
        pendingRequest = PdfExternalActionRequest.Save("", "second", "b.pdf")
        states["second"] = PdfDownloadState.Downloading

        // First completes
        states["first"] = PdfDownloadState.Ready(100L)
        if (pendingRequest?.stablePartId == "first") emittedEvents.add("first")

        // Second completes
        states["second"] = PdfDownloadState.Ready(200L)
        if (pendingRequest?.stablePartId == "second") {
            emittedEvents.add(pendingRequest!!::class.simpleName!!)
        }

        assertEquals("only second must auto-open with the correct action type",
            listOf("Save"), emittedEvents)
        // Verify second emits Save (not Open)
        val secondPending = pendingRequest
        assertTrue("pending request must be a Save action",
            secondPending is PdfExternalActionRequest.Save)
    }

    // ── Save action specific tests ───────────────────────────────

    @Test
    fun `Save click on Ready emits Save event`() {
        assertEquals("Ready must emit event for Save",
            ClickAction.EMIT_EVENT, clickAction(PdfDownloadState.Ready(4096L)))
    }

    @Test
    fun `Save click on Idle sets pending and downloads`() {
        assertEquals("Idle must set pending for Save",
            ClickAction.SET_PENDING_AND_DOWNLOAD, clickAction(PdfDownloadState.Idle))
    }

    @Test
    fun `Save click on Error retries then saves`() {
        assertEquals("Error must retry for Save",
            ClickAction.SET_PENDING_AND_DOWNLOAD,
            clickAction(PdfDownloadState.Error(PdfDownloadFailure.NETWORK)))
    }

    @Test
    fun `Save click on Downloading does nothing`() {
        assertEquals("Downloading must be no-op for Save",
            ClickAction.NO_OP, clickAction(PdfDownloadState.Downloading))
    }

    @Test
    fun `Save click on null sets pending and downloads`() {
        assertEquals("null state must start download for Save",
            ClickAction.SET_PENDING_AND_DOWNLOAD, clickAction(null))
    }

    // ── Pending action type preservation ─────────────────────────

    @Test
    fun `pending Save action emits Save after download completes`() {
        val stableId = "att_save"
        var pendingRequest: PdfExternalActionRequest? = PdfExternalActionRequest.Save(
            emailId = "msg_1", stablePartId = stableId, displayName = "doc.pdf"
        )
        val emittedActions = mutableListOf<String>()

        // Simulates: download completes while pending Save is set
        val state = PdfDownloadState.Ready(4096L)
        if (state is PdfDownloadState.Ready) {
            pendingRequest?.let { pending ->
                if (pending.stablePartId == stableId) {
                    emittedActions.add(pending::class.simpleName!!)
                    pendingRequest = null
                }
            }
        }

        assertEquals("must emit Save, not Open nor anything else",
            listOf("Save"), emittedActions)
        assertEquals("pending must be cleared after emit", null, pendingRequest)
    }

    @Test
    fun `pending Open action emits Open after download completes`() {
        val stableId = "att_open"
        var pendingRequest: PdfExternalActionRequest? = PdfExternalActionRequest.Open(
            emailId = "msg_1", stablePartId = stableId, displayName = "doc.pdf"
        )
        val emittedActions = mutableListOf<String>()

        val state = PdfDownloadState.Ready(4096L)
        if (state is PdfDownloadState.Ready) {
            pendingRequest?.let { pending ->
                if (pending.stablePartId == stableId) {
                    // Use class simpleName as proxy for type
                    when (pending) {
                        is PdfExternalActionRequest.Open -> emittedActions.add("Open")
                        is PdfExternalActionRequest.Save -> emittedActions.add("Save")
                    }
                    pendingRequest = null
                }
            }
        }

        assertEquals("must emit Open", listOf("Open"), emittedActions)
    }

    @Test
    fun `pending Save is replaced by Open when clicked afterwards`() {
        val stableId = "att_replace"
        var pendingRequest: PdfExternalActionRequest? = PdfExternalActionRequest.Save(
            emailId = "msg_1", stablePartId = stableId, displayName = "doc.pdf"
        )

        // User clicks Open on a different PDF (Ready) → emits immediately, clears pending
        pendingRequest = null

        // Then Save's download completes
        val state = PdfDownloadState.Ready(4096L)
        val emittedActions = mutableListOf<String>()
        if (state is PdfDownloadState.Ready) {
            pendingRequest?.let { pending ->
                if (pending.stablePartId == stableId) {
                    emittedActions.add("emitted")
                }
            }
        }

        assertTrue("nothing must be emitted after pending was cleared", emittedActions.isEmpty())
    }

    @Test
    fun `Open and Save on same Ready PDF both emit`() {
        // Clicking Open on Ready emits immediately
        assertEquals("Open on Ready must emit",
            ClickAction.EMIT_EVENT, clickAction(PdfDownloadState.Ready(100L)))
        // Clicking Save on Ready emits immediately
        assertEquals("Save on Ready must emit",
            ClickAction.EMIT_EVENT, clickAction(PdfDownloadState.Ready(100L)))
    }
}
