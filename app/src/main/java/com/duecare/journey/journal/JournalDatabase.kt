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
 * v2 schema: adds Party / FeePayment / LegalAssessment / RefundClaim
 * to the original JournalEntry. Version bumped from 1 to 2 — the
 * v0.1 skeleton uses fallbackToDestructiveMigration so existing
 * skeleton installs simply lose their (currently empty) journal.
 * v1 MVP will replace the destructive-migration call with a real
 * Migration(1, 2) that preserves data — but at v0.2 there's no real
 * data to preserve.
 *
 * Encryption key: derived from a hardware-backed Android Keystore key
 * (preferred) or an app-generated random key persisted in
 * EncryptedSharedPreferences (fallback for devices without a Keystore-
 * capable secure element). The key is generated on first launch and
 * never logged, transmitted, or backed up.
 *
 * IMPORTANT: this code never prints, logs, or returns the key to
 * caller code. Compromise of [JournalKeyVault.getOrCreateDatabaseKey]
 * is the worst-case scenario; treat it as the security boundary.
 */
@Database(
    entities = [
        JournalEntry::class,
        Party::class,
        FeePayment::class,
        LegalAssessment::class,
        RefundClaim::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(JournalConverters::class)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
    abstract fun partyDao(): PartyDao
    abstract fun feePaymentDao(): FeePaymentDao
    abstract fun legalAssessmentDao(): LegalAssessmentDao
    abstract fun refundClaimDao(): RefundClaimDao

    companion object {
        private const val DB_NAME = "duecare-journey.db"

        fun create(context: Context): JournalDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = JournalKeyVault.getOrCreateDatabaseKey(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, JournalDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()  // dev only; v1 MVP adds real Migration(1, 2)
                .build()
        }
    }
}
