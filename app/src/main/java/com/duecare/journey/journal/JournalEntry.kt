package com.duecare.journey.journal

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * One row in the worker's journey timeline. The DB this lives in is
 * SQLCipher-encrypted at rest with a key stored in Android Keystore;
 * see [JournalDatabase] for the encryption setup.
 *
 * Schema decisions:
 * - String IDs (UUIDs) so two journals can be merged without PK
 *   collisions if the worker ever migrates devices.
 * - `attachmentPath` is a relative path under the app's encrypted
 *   files dir; never an absolute path that could leak in a backup
 *   manifest.
 * - `gemmaAnalysis` and `grepHits` are populated lazily by the advice
 *   layer when the worker explicitly asks for analysis; we don't
 *   auto-analyze every entry because that would burn battery without
 *   consent.
 */
@Entity(tableName = "journal_entries")
@TypeConverters(JournalConverters::class)
data class JournalEntry(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val stage: JourneyStage,
    val kind: EntryKind,
    val title: String,
    val body: String,
    val attachmentPath: String? = null,
    val locationLatLng: String? = null,           // opt-in only
    val parties: List<String> = emptyList(),
    val taggedConcerns: List<String> = emptyList(),
    val gemmaAnalysis: String? = null,
    val grepHits: List<String> = emptyList(),
)

enum class JourneyStage {
    PRE_DEPARTURE,
    IN_TRANSIT,
    ARRIVED,
    EMPLOYED,
    EXIT,
}

enum class EntryKind {
    PHOTO,
    MESSAGE,
    DOCUMENT,
    NOTE,
    EXPENSE,
    INCIDENT,
}

/**
 * Room TypeConverters for the List<String> fields (parties,
 * taggedConcerns, grepHits) and the two enum fields.
 *
 * List separator: ASCII Unit Separator (U+001F). It's a non-printing
 * control character that doesn't appear in legitimate user text
 * (party names, concern tags, rule names) and survives a SQLite TEXT
 * round-trip cleanly. Using the `\u001F` escape rather than the
 * literal character keeps the source human-readable.
 */
class JournalConverters {

    private val sep = "\u001F"

    @TypeConverter
    fun stringListToString(value: List<String>): String =
        value.joinToString(sep)

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(sep)

    @TypeConverter
    fun stageToString(value: JourneyStage): String = value.name

    @TypeConverter
    fun stringToStage(value: String): JourneyStage = JourneyStage.valueOf(value)

    @TypeConverter
    fun kindToString(value: EntryKind): String = value.name

    @TypeConverter
    fun stringToKind(value: String): EntryKind = EntryKind.valueOf(value)
}
