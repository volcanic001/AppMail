package com.david.mailapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MailDatabase::class.java
    )

    private val testDbName = "mailapp-migration-5-6-test.db"

    @Before
    fun cleanupBefore() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext.deleteDatabase(testDbName)
    }

    @After
    fun cleanupAfter() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext.deleteDatabase(testDbName)
    }

    @Test
    fun migrate5To6_preservesAllEmailDataAndAddsNullableRfcColumns() {
        // 1. Create v5 database
        val v5Db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 5)

        // 2. Insert a row with all 17 v5 columns using representative values
        v5Db.execSQL(
            """INSERT INTO emails (
                id, thread_id, sender, sender_initials, recipient_to,
                subject, snippet, timestamp, is_read, is_starred,
                has_attachments, labels, folder, body, clean_body,
                pdf_attachments_json, pdf_metadata_scanned
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )"""
                .trimIndent(),
            arrayOf<Any>(
                "email-1",
                "thread-abcd",
                "María García <maria@test.com>",
                "MG",
                "destinatario@test.com",
                "Asunto con ñ y 😊",
                "Fragmento de prueba con acentos: café, déjà vu",
                1719619200000L,
                1,
                0,
                1,
                "INBOX,IMPORTANT",
                "inbox",
                "<html><body><p>Hola <b>mundo</b></p></body></html>",
                "Hola mundo",
                "[{\"fileName\":\"reporte.pdf\",\"mimeType\":\"application/pdf\",\"attachmentId\":\"att-xyz\",\"sizeBytes\":20480,\"partId\":\"part-1\"}]",
                1
            )
        )

        // 3. Close v5 database
        v5Db.close()

        // 4. Run migration, validating result against 6.json
        val v6Db: SupportSQLiteDatabase = helper.runMigrationsAndValidate(
            testDbName, 6, true, MailDatabase.MIGRATION_5_6
        )
        assertEquals("Database migrated to version 6", 6, v6Db.version)

        // 5. Query the migrated email and verify all 17 original columns
        val cursor = v6Db.query("SELECT * FROM emails WHERE id = ?", arrayOf("email-1"))
        assertTrue("Email row exists after migration", cursor.moveToFirst())

        assertEquals("id", "email-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("thread_id", "thread-abcd", cursor.getString(cursor.getColumnIndexOrThrow("thread_id")))
        // Unicode sender preserved
        assertEquals("sender", "María García <maria@test.com>", cursor.getString(cursor.getColumnIndexOrThrow("sender")))
        assertEquals("sender_initials", "MG", cursor.getString(cursor.getColumnIndexOrThrow("sender_initials")))
        assertEquals("recipient_to", "destinatario@test.com", cursor.getString(cursor.getColumnIndexOrThrow("recipient_to")))
        // Unicode + emoji subject preserved
        assertEquals("subject", "Asunto con ñ y 😊", cursor.getString(cursor.getColumnIndexOrThrow("subject")))
        assertEquals("snippet", "Fragmento de prueba con acentos: café, déjà vu", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
        assertEquals("timestamp", 1719619200000L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
        assertEquals("is_read", 1, cursor.getInt(cursor.getColumnIndexOrThrow("is_read")))
        assertEquals("is_starred", 0, cursor.getInt(cursor.getColumnIndexOrThrow("is_starred")))
        assertEquals("has_attachments", 1, cursor.getInt(cursor.getColumnIndexOrThrow("has_attachments")))
        assertEquals("labels", "INBOX,IMPORTANT", cursor.getString(cursor.getColumnIndexOrThrow("labels")))
        assertEquals("folder", "inbox", cursor.getString(cursor.getColumnIndexOrThrow("folder")))
        assertEquals("body", "<html><body><p>Hola <b>mundo</b></p></body></html>", cursor.getString(cursor.getColumnIndexOrThrow("body")))
        assertEquals("clean_body", "Hola mundo", cursor.getString(cursor.getColumnIndexOrThrow("clean_body")))
        assertEquals("pdf_attachments_json",
            "[{\"fileName\":\"reporte.pdf\",\"mimeType\":\"application/pdf\",\"attachmentId\":\"att-xyz\",\"sizeBytes\":20480,\"partId\":\"part-1\"}]",
            cursor.getString(cursor.getColumnIndexOrThrow("pdf_attachments_json")))
        assertEquals("pdf_metadata_scanned", 1, cursor.getInt(cursor.getColumnIndexOrThrow("pdf_metadata_scanned")))

        // 6. Verify RFC columns exist and are NULL
        assertNull("rfc_message_id is NULL", cursor.getString(cursor.getColumnIndexOrThrow("rfc_message_id")))
        assertTrue("rfc_message_id is null", cursor.isNull(cursor.getColumnIndexOrThrow("rfc_message_id")))
        assertNull("rfc_references is NULL", cursor.getString(cursor.getColumnIndexOrThrow("rfc_references")))
        assertTrue("rfc_references is null", cursor.isNull(cursor.getColumnIndexOrThrow("rfc_references")))

        cursor.close()

        // 7. PRAGMA table_info — verify 19 columns and RFC column types
        val pragmaCursor = v6Db.query("PRAGMA table_info(emails)")
        var columnCount = 0
        var foundRfcMessageId = false
        var foundRfcReferences = false

        while (pragmaCursor.moveToNext()) {
            columnCount++
            val name = pragmaCursor.getString(pragmaCursor.getColumnIndexOrThrow("name"))
            val type = pragmaCursor.getString(pragmaCursor.getColumnIndexOrThrow("type"))
            val notNull = pragmaCursor.getInt(pragmaCursor.getColumnIndexOrThrow("notnull"))
            val defaultValueIndex = pragmaCursor.getColumnIndexOrThrow("dflt_value")

            when (name) {
                "rfc_message_id" -> {
                    foundRfcMessageId = true
                    assertEquals("rfc_message_id type is TEXT", "TEXT", type)
                    assertEquals("rfc_message_id notnull=0", 0, notNull)
                    assertTrue(
                        "rfc_message_id has no default",
                        pragmaCursor.isNull(defaultValueIndex)
                    )
                }
                "rfc_references" -> {
                    foundRfcReferences = true
                    assertEquals("rfc_references type is TEXT", "TEXT", type)
                    assertEquals("rfc_references notnull=0", 0, notNull)
                    assertTrue(
                        "rfc_references has no default",
                        pragmaCursor.isNull(defaultValueIndex)
                    )
                }
            }
        }
        pragmaCursor.close()

        assertEquals("19 columns after migration", 19, columnCount)
        assertTrue("rfc_message_id column exists in table_info", foundRfcMessageId)
        assertTrue("rfc_references column exists in table_info", foundRfcReferences)

        // 8. Close v6 database
        v6Db.close()
    }
}
