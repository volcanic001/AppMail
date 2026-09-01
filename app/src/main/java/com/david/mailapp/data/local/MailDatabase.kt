package com.david.mailapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.data.local.entity.EmailEntity
@Database(
    entities = [EmailEntity::class],
    version = 7,
    exportSchema = true
)
abstract class MailDatabase : RoomDatabase() {

    abstract fun emailDao(): EmailDao

    companion object {
        @Volatile
        private var instance: MailDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emails ADD COLUMN rfc_message_id TEXT")
                db.execSQL("ALTER TABLE emails ADD COLUMN rfc_references TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emails ADD COLUMN content_state TEXT NOT NULL DEFAULT 'NOT_FETCHED'")
                db.execSQL("ALTER TABLE emails ADD COLUMN body_kind TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE emails ADD COLUMN inline_references_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE emails ADD COLUMN cached_content_bytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE emails ADD COLUMN content_last_access_epoch_ms INTEGER NOT NULL DEFAULT 0")

                // Normalización de datos existentes
                db.execSQL("""
                    UPDATE emails
                    SET content_state = CASE WHEN length(body) > 0 THEN 'READY' ELSE 'NOT_FETCHED' END,
                        body_kind = CASE WHEN length(body) > 0 THEN 'HTML' ELSE 'UNKNOWN' END,
                        cached_content_bytes = CASE WHEN length(body) > 0 THEN length(CAST(body AS BLOB)) + length(CAST(clean_body AS BLOB)) + 2 ELSE 0 END
                """.trimIndent())
            }
        }

        fun create(context: Context): MailDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MailDatabase::class.java,
                    "mailapp.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
