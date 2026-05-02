package com.duecare.journey.intel

import com.duecare.journey.intel.DomainKnowledge.GrepRules
import com.duecare.journey.journal.JournalEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the local GREP ruleset across journal entries and surfaces
 * structured risk objects. Pure function — no side effects, no I/O.
 *
 * Used by:
 *   - JournalRepository (or downstream callers) to populate
 *     [JournalEntry.taggedConcerns] and [JournalEntry.grepHits] when an
 *     entry is created.
 *   - NgoReportBuilder to compute the per-entry and corpus-level
 *     ILO indicator coverage that goes in the intake document.
 *   - The chat surface, which can prepend "I noticed N risk indicators
 *     in your journal" before answering a worker question, so the
 *     model has structured context to reason about.
 */
@Singleton
class RiskAnalyzer @Inject constructor() {

    data class Risk(
        val ruleId: String,
        val displayName: String,
        val iloIndicator: Int,
        val severity: GrepRules.Severity,
        val statuteCitation: String,
        val whatItMeans: String,
        val nextStep: String,
        val sourceEntryId: String,
        val sourceEntryTitle: String,
        val matchedSnippet: String,
    )

    /** Analyze a single entry and return any risks fired. */
    fun analyze(entry: JournalEntry): List<Risk> {
        val haystack = "${entry.title}\n${entry.body}"
        return GrepRules.match(haystack).map { rule ->
            val match = rule.pattern.find(haystack)
            Risk(
                ruleId = rule.id,
                displayName = rule.displayName,
                iloIndicator = rule.iloIndicator,
                severity = rule.severity,
                statuteCitation = rule.statuteCitation,
                whatItMeans = rule.whatItMeans,
                nextStep = rule.nextStep,
                sourceEntryId = entry.id,
                sourceEntryTitle = entry.title,
                matchedSnippet = match?.value.orEmpty(),
            )
        }
    }

    /** Analyze a corpus of entries and dedupe by (ruleId, sourceEntryId). */
    fun analyzeAll(entries: List<JournalEntry>): List<Risk> {
        val out = mutableListOf<Risk>()
        for (entry in entries) {
            out.addAll(analyze(entry))
        }
        // Sort: highest severity first, then most-recent entry timestamp
        val byEntryTs = entries.associateBy({ it.id }, { it.timestampMillis })
        return out.sortedWith(
            compareBy<Risk> { it.severity.ordinal }
                .thenByDescending { byEntryTs[it.sourceEntryId] ?: 0L }
        )
    }

    /** ILO indicator histogram across the corpus. Used by the report
     *  generator to render a coverage chart and by the chat surface to
     *  give Gemma an "ILO indicator profile" of the worker's situation
     *  (without leaking raw text). */
    fun iloIndicatorHistogram(risks: List<Risk>): Map<Int, Int> =
        risks.groupingBy { it.iloIndicator }.eachCount()
}
