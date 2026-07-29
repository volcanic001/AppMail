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
    version = 6,
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

        fun create(context: Context): MailDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MailDatabase::class.java,
                    "mailapp.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
