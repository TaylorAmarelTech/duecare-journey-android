package com.duecare.journey.journal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * SQLCipher-encrypted Room database for the journal.
 *
 * Encryption key: derived from a hardware-backed Android Keystore key
 * (preferred) or an app-generated random key persisted in EncryptedSharedPreferences
 * (fallback for devices without a Keystore-capable secure element).
 * The key is generated on first launch and never logged, transmitted,
 * or backed up.
 *
 * IMPORTANT: this code never prints, logs, or returns the key to
 * caller code. Compromise of `getOrCreateDatabaseKey` is the worst-
 * case scenario; treat it as the security boundary.
 */
@Database(
    entities = [JournalEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(JournalConverters::class)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        private const val DB_NAME = "duecare-journey.db"

        fun create(context: Context): JournalDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = JournalKeyVault.getOrCreateDatabaseKey(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, JournalDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()  // dev only; real migrations land with v2
                .build()
        }
    }
}
