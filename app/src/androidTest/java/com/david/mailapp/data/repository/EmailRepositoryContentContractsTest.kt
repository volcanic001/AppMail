package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.core.session.SessionWriteGuardImpl
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.data.pdf.PdfCacheManager
import com.david.mailapp.data.remote.provider.BodyFetchResult
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.domain.model.PdfAttachmentMetadata
import com.david.mailapp.feature.emaildetail.components.EmailHtmlCleaner
import com.david.mailapp.testhelpers.FakeEmailProvider
import com.david.mailapp.testhelpers.FakeSessionWriteGuard
import com.david.mailapp.testhelpers.testEmail
import java.io.File
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contratos de [EmailRepository.fetchAndCacheBody], [EmailRepository.downloadInlineImages]
 * y [EmailRepository.injectInlineImages]: retorno de la misma instancia, delegación
 * exacta del emailId, persistencia atómica de body crudo, body limpio y metadata PDF,
 * normalización de cuerpo nulo y lista PDF vacía autoritativa (subfase 3.2); ausencias
 * de lease/proveedor/resultado, errores y cancelación remotos, cambio de sesión durante
 * la descarga y fallo local de commit (subfase 3.3); imágenes inline con delegación
 * exacta y las tres variantes CID, incluida la sensibilidad a prefijos y orden del mapa
 * como comportamiento heredado (subfase 3.4).
 */
class EmailRepositoryContentContractsTest {

    private lateinit var db: MailDatabase
    private lateinit var fakeProvider: FakeEmailProvider
    private lateinit var fakeWriteGuard: FakeSessionWriteGuard
    private lateinit var repository: EmailRepository
    private lateinit var cacheDir: File
    private lateinit var events: MutableList<String>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        fakeProvider = FakeEmailProvider()
        fakeWriteGuard = FakeSessionWriteGuard()
        events = mutableListOf()
        fakeProvider.eventLog = events
        fakeWriteGuard.eventLog = events
        cacheDir = File(context.cacheDir, "pdf_test_${System.nanoTime()}")
        cacheDir.mkdirs()
        repository = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = fakeWriteGuard
        )
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
    }

    // ═══════════════════════════════════════════════════════════════
    // C1 — Cuerpo HTML completo y persistencia atómica
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c1_fetchAndCacheBody_complete_html_and_pdf_metadata_persist_atomically() = runTest {
        val rawHtml = """<html><body style="background-color:#ffffff;color:#333333"><p>Hello <b>world</b></p></body></html>"""
        val expectedClean = EmailHtmlCleaner.clean(rawHtml)
        val pdfs = listOf(
            PdfAttachmentMetadata("report.pdf", "application/pdf", "att-1", 1_024L, partId = "0.1"),
            PdfAttachmentMetadata("plan.pdf", "application/pdf", "att-2", 2_048L, partId = "0.2")
        )
        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail(id = "e1", folder = EmailFolder.Inbox).copy(
                        isRead = true,
                        labels = listOf("IMPORTANT", "Personal"),
                        rfcMessageId = "<msg-1@test.com>",
                        rfcReferences = "<ref-a@test.com>"
                    ),
                    EmailFolder.Inbox
                )
            )
        )
        val before = get("e1")!!

        // Observar el Flow de Room desde el estado semilla
        val emissions = Channel<EmailEntity?>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.IO) {
            db.emailDao().getById("e1").collect(emissions::send)
        }
        emissions.awaitValue { it?.id == "e1" && it.body.isEmpty() }

        val result = BodyFetchResult(
            rawBody = rawHtml,
            contentState = com.david.mailapp.domain.model.EmailContentState.READY,
            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.HTML,
            inlineRefs = listOf(com.david.mailapp.domain.model.EmailInlineReference("cid:img1", "att-img-1", "image/png")),
            pdfAttachments = pdfs
        )
        fakeProvider.fetchBodyResult = result

        val returned = repository.fetchAndCacheBody("e1")

        assertSame("Same BodyFetchResult instance must be returned", result, returned)
        assertEquals(listOf("e1"), fakeProvider.receivedFetchBodyIds)
        assertEquals(1, fakeProvider.fetchBodyCalls)
        assertEquals(listOf("gmail.fetchBody", "room.commit"), events)

        // Una sola actualización con todos los campos ya consistentes
        val finalEmission = emissions.awaitNext()!!
        assertEquals(rawHtml, finalEmission.body)
        assertEquals(expectedClean, finalEmission.cleanBody)
        assertEquals(true, finalEmission.pdfMetadataScanned)
        assertEquals(true, finalEmission.hasAttachments)
        assertEquals(pdfs, finalEmission.toDomain().pdfAttachments)

        // Fila final y preservación de campos no relacionados
        val after = get("e1")!!
        assertEquals(rawHtml, after.body)
        assertEquals(expectedClean, after.cleanBody)
        assertEquals(true, after.pdfMetadataScanned)
        assertEquals(true, after.hasAttachments)
        assertEquals(pdfs, after.toDomain().pdfAttachments)
        assertUnrelatedFieldsPreserved(before, after)
    }

    // ═══════════════════════════════════════════════════════════════
    // C2 — Cuerpo nulo con metadatos PDF
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c2_fetchAndCacheBody_null_body_preserves_body_and_persists_pdf_metadata() = runTest {
        val pdfs = listOf(
            PdfAttachmentMetadata("invoice.pdf", "application/pdf", "att-3", 512L, partId = "0.1")
        )
        val oldHtml = """<html><body><p>Old <i>stored</i> body</p></body></html>"""
        val oldClean = EmailHtmlCleaner.clean(oldHtml)

        // Fila inicialmente vacía y fila con cuerpo previamente almacenado
        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(testEmail(id = "e2-empty"), EmailFolder.Inbox),
                EmailEntity.fromDomain(
                    testEmail(id = "e2-stored").copy(body = oldHtml, cleanBody = oldClean),
                    EmailFolder.Inbox
                )
            )
        )
        val beforeEmpty = get("e2-empty")!!
        val beforeStored = get("e2-stored")!!

        fakeProvider.fetchBodyResult = BodyFetchResult(
            rawBody = null,
            contentState = com.david.mailapp.domain.model.EmailContentState.EMPTY,
            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.UNKNOWN,
            inlineRefs = emptyList(),
            pdfAttachments = pdfs
        )

        val returnedEmpty = repository.fetchAndCacheBody("e2-empty")
        val returnedStored = repository.fetchAndCacheBody("e2-stored")

        assertSame(fakeProvider.fetchBodyResult, returnedEmpty)
        assertSame(fakeProvider.fetchBodyResult, returnedStored)
        assertEquals(listOf("e2-empty", "e2-stored"), fakeProvider.receivedFetchBodyIds)
        assertEquals(
            listOf("gmail.fetchBody", "room.commit", "gmail.fetchBody", "room.commit"),
            events
        )

        // La fila vacía se mantiene vacía pero gana metadata PDF
        val emptyAfter = get("e2-empty")!!
        assertEquals("", emptyAfter.body)
        assertEquals("", emptyAfter.cleanBody)
        assertEquals(true, emptyAfter.pdfMetadataScanned)
        assertEquals(true, emptyAfter.hasAttachments)
        assertEquals(pdfs, emptyAfter.toDomain().pdfAttachments)
        assertUnrelatedFieldsPreserved(beforeEmpty, emptyAfter)

        // La fila con cuerpo conserva body/cleanBody preexistentes y gana metadata PDF
        val storedAfter = get("e2-stored")!!
        assertEquals(oldHtml, storedAfter.body)
        assertEquals(oldClean, storedAfter.cleanBody)
        assertEquals(true, storedAfter.pdfMetadataScanned)
        assertEquals(true, storedAfter.hasAttachments)
        assertEquals(pdfs, storedAfter.toDomain().pdfAttachments)
        assertUnrelatedFieldsPreserved(beforeStored, storedAfter)
    }

    // ═══════════════════════════════════════════════════════════════
    // C3 — Lista PDF vacía como resultado autoritativo
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c3_fetchAndCacheBody_empty_pdf_list_replaces_old_metadata() = runTest {
        val oldMeta = PdfAttachmentMetadata("old.pdf", "application/pdf", "att-old", 999L, partId = "0.9")
        val oldHtml = """<html><body><p>Old body</p></body></html>"""
        val newHtml = """<html><body><p>New <b>body</b></p></body></html>"""
        val expectedClean = EmailHtmlCleaner.clean(newHtml)

        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail(id = "e3").copy(
                        body = oldHtml,
                        cleanBody = EmailHtmlCleaner.clean(oldHtml),
                        pdfAttachments = listOf(oldMeta),
                        pdfMetadataScanned = true
                    ),
                    EmailFolder.Inbox
                )
            )
        )
        val before = get("e3")!!
        assertEquals(true, before.pdfMetadataScanned)
        assertEquals(true, before.hasAttachments)
        assertEquals(listOf(oldMeta), before.toDomain().pdfAttachments)

        fakeProvider.fetchBodyResult = BodyFetchResult(
            rawBody = newHtml,
            contentState = com.david.mailapp.domain.model.EmailContentState.READY,
            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.HTML,
            inlineRefs = emptyList(),
            pdfAttachments = emptyList()
        )

        val returned = repository.fetchAndCacheBody("e3")

        assertSame(fakeProvider.fetchBodyResult, returned)
        assertEquals(listOf("e3"), fakeProvider.receivedFetchBodyIds)
        assertEquals(listOf("gmail.fetchBody", "room.commit"), events)

        val after = get("e3")!!
        assertEquals(newHtml, after.body)
        assertEquals(expectedClean, after.cleanBody)
        assertEquals(true, after.pdfMetadataScanned)
        assertEquals(false, after.hasAttachments)
        assertEquals("[]", after.pdfAttachmentsJson)
        assertEquals(emptyList<PdfAttachmentMetadata>(), after.toDomain().pdfAttachments)
        assertUnrelatedFieldsPreserved(before, after)
    }

    // ═══════════════════════════════════════════════════════════════
    // C4 — Lease ausente
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c4_fetchAndCacheBody_without_lease_returns_null_without_remote_or_commit() = runTest {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = "e4"), EmailFolder.Inbox))
        )
        val before = get("e4")!!
        fakeWriteGuard.captureResult = null

        val returned = repository.fetchAndCacheBody("e4")

        assertNull(returned)
        assertEquals(0, fakeProvider.fetchBodyCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertTrue(events.isEmpty())
        assertEquals(before, get("e4")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C5 — Proveedor ausente
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c5_fetchAndCacheBody_without_provider_returns_null_without_commit() = runTest {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = "e5"), EmailFolder.Inbox))
        )
        val before = get("e5")!!
        val repositoryWithoutProvider = EmailRepository(
            database = db, providerFactory = { null },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = fakeWriteGuard
        )

        val returned = repositoryWithoutProvider.fetchAndCacheBody("e5")

        assertNull(returned)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertTrue(events.isEmpty())
        assertEquals(before, get("e5")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C6 — Resultado remoto nulo
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c6_fetchAndCacheBody_null_remote_result_returns_null_room_intact() = runTest {
        val oldHtml = """<html><body><p>Existing body</p></body></html>"""
        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail(id = "e6").copy(
                        body = oldHtml,
                        cleanBody = EmailHtmlCleaner.clean(oldHtml),
                        pdfAttachments = listOf(
                            PdfAttachmentMetadata("existing.pdf", "application/pdf", "att-x", 42L)
                        ),
                        pdfMetadataScanned = true
                    ),
                    EmailFolder.Inbox
                )
            )
        )
        val before = get("e6")!!
        fakeProvider.fetchBodyResult = null

        val returned = repository.fetchAndCacheBody("e6")

        assertNull(returned)
        assertEquals(1, fakeProvider.fetchBodyCalls)
        assertEquals(listOf("e6"), fakeProvider.receivedFetchBodyIds)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.fetchBody"), events)
        assertEquals(before, get("e6")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C7 — Excepción remota ordinaria
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c7_fetchAndCacheBody_remote_error_propagates_same_instance_room_intact() = runTest {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = "e7"), EmailFolder.Inbox))
        )
        val before = get("e7")!!
        val sentinel = IOException("sentinel remote failure")
        fakeProvider.fetchBodyError = sentinel

        val thrown = try {
            repository.fetchAndCacheBody("e7")
            null
        } catch (e: IOException) {
            e
        }

        assertSame(sentinel, thrown)
        assertEquals(1, fakeProvider.fetchBodyCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.fetchBody"), events)
        assertEquals(before, get("e7")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C8 — Cancelación remota
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c8_fetchAndCacheBody_remote_cancellation_propagates_same_instance_room_intact() = runTest {
        db.emailDao().upsertAll(
            listOf(EmailEntity.fromDomain(testEmail(id = "e8"), EmailFolder.Inbox))
        )
        val before = get("e8")!!
        val sentinel = CancellationException("sentinel remote cancellation")
        fakeProvider.fetchBodyError = sentinel

        val thrown = try {
            repository.fetchAndCacheBody("e8")
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(sentinel, thrown)
        assertEquals(1, fakeProvider.fetchBodyCalls)
        assertEquals(0, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.fetchBody"), events)
        assertEquals(before, get("e8")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C9 — Cambio de sesión durante la descarga
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c9_fetchAndCacheBody_session_change_rejects_commit_and_returns_old_result() = runTest {
        val realGuard = SessionWriteGuardImpl()
        realGuard.activate() // generación 1
        val sessionRepo = EmailRepository(
            database = db, providerFactory = { fakeProvider },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = realGuard
        )

        val gate = CompletableDeferred<Unit>()
        fakeProvider.fetchBodyDeferred = gate
        fakeProvider.fetchBodyStarted = CompletableDeferred()
        val oldResult = BodyFetchResult(
            rawBody = "<html><body><p>old session body</p></body></html>",
            contentState = com.david.mailapp.domain.model.EmailContentState.READY,
            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.HTML,
            inlineRefs = emptyList(),
            pdfAttachments = listOf(PdfAttachmentMetadata("old.pdf", "application/pdf", "att-old", 100L))
        )
        fakeProvider.fetchBodyResult = oldResult

        val job = async { sessionRepo.fetchAndCacheBody("e9") }
        fakeProvider.fetchBodyStarted!!.await() // el lease de la generación 1 ya está capturado

        // La sesión cambia mientras la descarga está pendiente
        realGuard.invalidate()
        realGuard.activate() // generación 2

        // Fila representativa de la cuenta nueva antes de liberar el proveedor
        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail(id = "e9", folder = EmailFolder.Inbox, subject = "new-session"),
                    EmailFolder.Inbox
                )
            )
        )

        gate.complete(Unit)
        val returned = job.await()

        // Comportamiento heredado: el resultado remoto antiguo se devuelve
        assertSame(oldResult, returned)
        assertEquals(listOf("e9"), fakeProvider.receivedFetchBodyIds)

        // El commit del lease antiguo es rechazado: la fila de la sesión nueva no se contamina
        val after = get("e9")!!
        assertEquals("new-session", after.subject)
        assertEquals("", after.body)
        assertEquals("", after.cleanBody)
        assertEquals(false, after.pdfMetadataScanned)
        assertEquals(false, after.hasAttachments)
    }

    // ═══════════════════════════════════════════════════════════════
    // C10 — Fallo local de commit
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c10_fetchAndCacheBody_commit_failure_propagates_and_preserves_entity() = runTest {
        val html = """<html><body><p>Committed body</p></body></html>"""
        db.emailDao().upsertAll(
            listOf(
                EmailEntity.fromDomain(
                    testEmail(id = "e10").copy(
                        body = html,
                        cleanBody = EmailHtmlCleaner.clean(html),
                        pdfAttachments = listOf(
                            PdfAttachmentMetadata("plan.pdf", "application/pdf", "att-p", 256L)
                        ),
                        pdfMetadataScanned = true
                    ),
                    EmailFolder.Inbox
                )
            )
        )
        val before = get("e10")!!
        val sentinel = IllegalStateException("sentinel commit failure")
        fakeWriteGuard.commitError = sentinel
        fakeProvider.fetchBodyResult = BodyFetchResult(
            rawBody = "<html><body><p>new remote body</p></body></html>",
            contentState = com.david.mailapp.domain.model.EmailContentState.READY,
            bodyKind = com.david.mailapp.domain.model.EmailBodyKind.HTML,
            inlineRefs = emptyList(),
            pdfAttachments = emptyList()
        )

        val thrown = try {
            repository.fetchAndCacheBody("e10")
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertSame(sentinel, thrown)
        assertEquals(1, fakeWriteGuard.commitCalls)
        assertEquals(listOf("gmail.fetchBody", "room.commit"), events)
        assertEquals(before, get("e10")!!)
    }

    // ═══════════════════════════════════════════════════════════════
    // C11 — Referencias vacías en downloadInlineImages
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c11_downloadInlineImages_empty_refs_returns_empty_without_resolving_provider() = runTest {
        var providerFactoryReads = 0
        val countingRepo = EmailRepository(
            database = db,
            providerFactory = {
                providerFactoryReads++
                fakeProvider
            },
            pdfCacheManager = PdfCacheManager(cacheDir),
            writeGuard = fakeWriteGuard
        )

        val returned = countingRepo.downloadInlineImages("e11", emptyList())

        assertTrue(returned.isEmpty())
        assertEquals("Provider must not be resolved for empty refs", 0, providerFactoryReads)
        assertEquals(0, fakeProvider.inlineImagesCalls)
    }

    // ═══════════════════════════════════════════════════════════════
    // C12 — Éxito parcial con delegación exacta
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c12_downloadInlineImages_delegates_exact_refs_order_and_returns_same_map() = runTest {
        val refs = listOf(
            com.david.mailapp.domain.model.EmailInlineReference("img1", "att-1", "image/png"),
            com.david.mailapp.domain.model.EmailInlineReference("img2", "att-2", "image/jpeg"),
            com.david.mailapp.domain.model.EmailInlineReference("img3", "att-3", "image/gif")
        )
        val map = linkedMapOf(
            "img1" to "data:image/png;base64,AAA",
            "img2" to "data:image/jpeg;base64,BBB"
        )
        fakeProvider.inlineImagesResult = map

        val returned = repository.downloadInlineImages("e12", refs)

        assertSame("Same map instance must be returned unfiltered", map, returned)
        assertEquals(1, fakeProvider.inlineImagesCalls)
        assertEquals(listOf("e12" to refs), fakeProvider.receivedInlineImageRequests)
    }

    // ═══════════════════════════════════════════════════════════════
    // C13 — Provider ausente en downloadInlineImages
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c13_downloadInlineImages_without_provider_returns_empty_map() = runTest {
        val repositoryWithoutProvider = EmailRepository(
            database = db, providerFactory = { null },
            pdfCacheManager = PdfCacheManager(cacheDir), writeGuard = fakeWriteGuard
        )

        val returned = repositoryWithoutProvider.downloadInlineImages(
            "e13", listOf(com.david.mailapp.domain.model.EmailInlineReference("img1", "att-1", "image/png"))
        )

        assertTrue(returned.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // C14 — Excepción ordinaria en downloadInlineImages
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c14_downloadInlineImages_remote_error_propagates_same_instance() = runTest {
        val sentinel = IOException("sentinel inline failure")
        fakeProvider.inlineImagesError = sentinel

        val thrown = try {
            repository.downloadInlineImages(
                "e14", listOf(com.david.mailapp.domain.model.EmailInlineReference("img1", "att-1", "image/png"))
            )
            null
        } catch (e: IOException) {
            e
        }

        assertSame(sentinel, thrown)
    }

    // ═══════════════════════════════════════════════════════════════
    // C15 — Cancelación en downloadInlineImages
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c15_downloadInlineImages_remote_cancellation_propagates_same_instance() = runTest {
        val sentinel = CancellationException("sentinel inline cancellation")
        fakeProvider.inlineImagesError = sentinel

        val thrown = try {
            repository.downloadInlineImages(
                "e15", listOf(com.david.mailapp.domain.model.EmailInlineReference("img1", "att-1", "image/png"))
            )
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(sentinel, thrown)
    }

    // ═══════════════════════════════════════════════════════════════
    // C16 — Mapa de imágenes vacío en injectInlineImages
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c16_injectInlineImages_empty_map_returns_same_html_instance() {
        val html = """<img src="cid:img1">"""
        val returned = repository.injectInlineImages(html, emptyMap())
        assertSame(html, returned)
    }

    // ═══════════════════════════════════════════════════════════════
    // C17 — Sustitución de las tres variantes CID
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c17_injectInlineImages_replaces_all_three_cid_variants_everywhere() {
        val html = """<img src="cid:img1"><img src="cid:img1"><img src="cid:&lt;img1&gt;"><img src="cid:<img1>">"""
        val map = linkedMapOf("img1" to "data:image/png;base64,AAA")
        val expected = """<img src="data:image/png;base64,AAA"><img src="data:image/png;base64,AAA"><img src="data:image/png;base64,AAA"><img src="data:image/png;base64,AAA">"""
        assertEquals(expected, repository.injectInlineImages(html, map))
    }

    // ═══════════════════════════════════════════════════════════════
    // C18 — Sin coincidencia y diferencias de mayúsculas
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c18_injectInlineImages_no_match_and_case_mismatch_leave_html_unchanged() {
        val html = """<img src="cid:other"><img src="CID:img1"><img src="Cid:img1">"""
        val map = linkedMapOf("img1" to "data:image/png;base64,AAA")
        assertEquals(html, repository.injectInlineImages(html, map))
    }

    // ═══════════════════════════════════════════════════════════════
    // C19 — IDs similares: sensibilidad al orden y al prefijo
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun c19_injectInlineImages_similar_ids_depend_on_map_order_prefix_replaced() {
        val html = """<img src="cid:img"><img src="cid:img2">"""

        // CID corto primero: su prefijo también reemplaza el inicio del CID largo
        val shortFirst = linkedMapOf("img" to "DATA_SHORT", "img2" to "DATA_LONG")
        assertEquals(
            """<img src="DATA_SHORT"><img src="DATA_SHORT2">""",
            repository.injectInlineImages(html, shortFirst)
        )

        // CID largo primero: ambos se sustituyen correctamente
        val longFirst = linkedMapOf("img2" to "DATA_LONG", "img" to "DATA_SHORT")
        assertEquals(
            """<img src="DATA_SHORT"><img src="DATA_LONG">""",
            repository.injectInlineImages(html, longFirst)
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private suspend fun get(id: String) = db.emailDao().getEntitiesByIdsSync(listOf(id)).firstOrNull()

    private fun assertUnrelatedFieldsPreserved(before: EmailEntity, after: EmailEntity) {
        assertEquals(before.id, after.id)
        assertEquals(before.threadId, after.threadId)
        assertEquals(before.from, after.from)
        assertEquals(before.fromInitials, after.fromInitials)
        assertEquals(before.to, after.to)
        assertEquals(before.subject, after.subject)
        assertEquals(before.snippet, after.snippet)
        assertEquals(before.timestamp, after.timestamp)
        assertEquals(before.isRead, after.isRead)
        assertEquals(before.isStarred, after.isStarred)
        assertEquals(before.labels, after.labels)
        assertEquals(before.folder, after.folder)
        assertEquals(before.rfcMessageId, after.rfcMessageId)
        assertEquals(before.rfcReferences, after.rfcReferences)
    }

    private suspend fun <T> Channel<T>.awaitValue(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000L) {
                while (true) {
                    val value = receive()
                    if (predicate(value)) return@withTimeout value
                }
                error("Unreachable")
            }
        }

    private suspend fun <T> Channel<T>.awaitNext(): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000L) { receive() }
        }
}
