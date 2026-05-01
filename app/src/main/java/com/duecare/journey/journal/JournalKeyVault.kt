package com.duecare.journey.journal

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Manages the SQLCipher passphrase for the encrypted journal DB.
 *
 * Key generation:
 * - First launch: generate 32 bytes of random data, persist via
 *   EncryptedSharedPreferences (which itself uses a hardware-backed
 *   AES key from Android Keystore where available).
 * - Subsequent launches: read back the same passphrase.
 *
 * NEVER:
 * - Log the key.
 * - Persist it to ordinary SharedPreferences, regular files, or
 *   any backup-eligible location.
 * - Return it to caller code outside this class.
 *
 * STATUS (skeleton): the EncryptedSharedPreferences integration is
 * stubbed for the v0.1 skeleton — real key persistence lands in v1
 * MVP. Per `docs/android_app_architecture.md` privacy posture, the
 * real impl uses [androidx.security:security-crypto] (already on the
 * dependency list).
 */
internal object JournalKeyVault {

    private const val PREFS_NAME = "duecare-journal-keys"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        // TODO(v1): replace with EncryptedSharedPreferences-backed
        // implementation. The skeleton returns an in-memory random
        // key so the build/test cycle works, but a real install
        // would lose all journal data on app restart.
        return SecureRandom().generateSeed(32)
    }
}
