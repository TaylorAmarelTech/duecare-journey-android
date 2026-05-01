package com.duecare.journey.journal

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for journal reads/writes. The advice layer
 * and the export layer depend on this, NOT on the DAO directly.
 */
@Singleton
class JournalRepository @Inject constructor(
    private val dao: JournalDao,
) {

    fun observeAll(): Flow<List<JournalEntry>> = dao.observeAll()

    fun observeByStage(stage: JourneyStage): Flow<List<JournalEntry>> =
        dao.observeByStage(stage)

    suspend fun recent(limit: Int = 10): List<JournalEntry> = dao.recent(limit)

    suspend fun currentStage(): JourneyStage = dao.currentStage()
        ?: JourneyStage.PRE_DEPARTURE

    suspend fun add(
        stage: JourneyStage,
        kind: EntryKind,
        title: String,
        body: String,
        attachmentPath: String? = null,
        parties: List<String> = emptyList(),
        taggedConcerns: List<String> = emptyList(),
    ) {
        dao.upsert(
            JournalEntry(
                id = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                stage = stage,
                kind = kind,
                title = title,
                body = body,
                attachmentPath = attachmentPath,
                parties = parties,
                taggedConcerns = taggedConcerns,
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Best-effort corridor inference from journal contents.
     *  Used by the advice layer for context-aware prompting. */
    suspend fun detectedCorridor(): String? {
        val recent = dao.recent(50)
        // crude heuristic: scan parties + body for known corridor markers.
        // v1 MVP replaces this with a structured `corridor` field
        // captured at journal creation time.
        val text = recent.joinToString(" ") { "${it.title} ${it.body}" }
            .lowercase()
        return when {
            "hong kong" in text && "philippines" in text -> "PH-HK"
            "hong kong" in text && "indonesia" in text -> "ID-HK"
            "saudi" in text && "philippines" in text -> "PH-SA"
            "saudi" in text && "nepal" in text -> "NP-SA"
            "qatar" in text && "nepal" in text -> "NP-QA"
            "saudi" in text && "bangladesh" in text -> "BD-SA"
            else -> null
        }
    }

    /** Panic wipe: destroys all journal entries. Per the architecture
     *  doc privacy posture, this is one of the worst-case actions and
     *  is gated by a 3-tap-and-hold + biometric confirm in the UI. */
    suspend fun panicWipe() {
        dao.deleteAll()
        // v1 MVP also: destroy SQLCipher key, overwrite attachment files,
        // recommend uninstall.
    }
}
