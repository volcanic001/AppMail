package com.david.mailapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.data.local.MailDatabase
import com.david.mailapp.data.local.entity.EmailEntity
import com.david.mailapp.domain.model.EmailFolder
import com.david.mailapp.testhelpers.testHeavyEmail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailRepositoryMaterializationTest {

    private lateinit var database: MailDatabase
    private lateinit var repository: EmailMailboxCoordinator

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MailDatabase::class.java
        ).build()
        repository = EmailMailboxCoordinator(
            dao = database.emailDao(),
            providerFactory = { null },
            writeGuard = com.david.mailapp.testhelpers.FakeSessionWriteGuard()
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun observeInbox_with_heavy_emails_does_not_overflow_cursor_window() = runTest {
        // Insert 30 heavy emails (each ~1MB body, plus overhead), total ~30MB.
        // Android CursorWindow is strictly 2MB. If projection were not lightweight,
        // this would trigger CursorWindowAllocationException or OOM immediately upon read.
        val emails = (1..30).map { i ->
            EmailEntity.fromDomain(
                testHeavyEmail("heavy_$i", sizeBytes = 1_000_000),
                EmailFolder.Inbox
            )
        }

        // We insert in chunks to avoid blowing up the Binder limit during IPC (upsertAll).
        // Since Room runs in the same process in tests, it's generally fine, but we chunk it anyway.
        emails.chunked(10).forEach { chunk ->
            database.emailDao().upsertAll(chunk)
        }

        // Reading the inbox flow should complete almost instantaneously without OOM or cursor errors.
        val inbox = repository.getInbox().first()

        assertEquals(30, inbox.size)

        // Verify that bodies are indeed mapped as lightweight (empty)
        inbox.forEach { email ->
            assertEquals("", email.body)
            assertEquals("", email.cleanBody)
        }
    }
}
