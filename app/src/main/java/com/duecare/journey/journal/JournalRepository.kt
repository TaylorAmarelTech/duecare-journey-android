package com.duecare.journey.journal

import com.duecare.journey.intel.RiskAnalyzer
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for journal reads/writes. The advice layer
 * and the export layer depend on this, NOT on the DAO directly.
 *
 * v0.6: every new entry is run through [RiskAnalyzer] at add time so
 * its `taggedConcerns` and `grepHits` are populated automatically. The
 * worker never has to remember to tag — if their entry text matches an
 * ILO indicator pattern, the Reports tab and the chat surface see it
 * the next time they're rendered.
 */
@Singleton
class JournalRepository @Inject constructor(
    private val dao: JournalDao,
    private val riskAnalyzer: RiskAnalyzer,
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
        val draft = JournalEntry(
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
        // Auto-populate risk tags + grep hits so downstream surfaces
        // (Reports, Chat) can use them without re-analysis. Worker-set
        // taggedConcerns are preserved in addition.
        val risks = riskAnalyzer.analyze(draft)
        val autoTags = risks.map { it.displayName }.distinct()
        val grepHits = risks.map { it.ruleId }.distinct()
        dao.upsert(
            draft.copy(
                taggedConcerns = (taggedConcerns + autoTags).distinct(),
                grepHits = grepHits,
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Best-effort corridor inference from journal contents.
     *  Used by the advice layer for context-aware prompting. */
    suspend fun detectedCorridor(): String? {
        val recent = dao.recent(50)
        // crude heuristic: scan parties + body for known corridor markers.
        // Covers the 12 corridors in DomainKnowledge.CorridorKnowledge.
        val text = recent.joinToString(" ") { "${it.title} ${it.body}" }
            .lowercase()
        return when {
            // Asia → Gulf / Hong Kong / Singapore (the original 6)
            "hong kong" in text && "philippines" in text -> "PH-HK"
            "hong kong" in text && "indonesia" in text -> "ID-HK"
            "saudi" in text && "philippines" in text -> "PH-SA"
            "saudi" in text && "nepal" in text -> "NP-SA"
            "qatar" in text && "nepal" in text -> "NP-QA"
            "saudi" in text && "bangladesh" in text -> "BD-SA"
            "singapore" in text && "indonesia" in text -> "ID-SG"
            // Latin America
            ("united states" in text || "usa" in text || "u.s." in text) &&
                ("mexico" in text || "mexican" in text) -> "MX-US"
            "colombia" in text && "venezuela" in text -> "VE-CO"
            // West Africa → Lebanon (kafala)
            "lebanon" in text && ("ghana" in text || "ghanaian" in text) -> "GH-LB"
            "lebanon" in text && ("nigeria" in text || "nigerian" in text) -> "NG-LB"
            // Refugee corridors
            "germany" in text && ("syria" in text || "syrian" in text) -> "SY-DE"
            "poland" in text && ("ukraine" in text || "ukrainian" in text) -> "UA-PL"
            else -> null
        }
    }

    /** Panic wipe: destroys all journal entries. Per the architecture
     *  doc privacy posture, this is one of the worst-case actions and
     *  is gated by a 3-tap-and-hold + biometric confirm in the UI. */
    suspend fun panicWipe() {
        dao.deleteAll()
    }
}
