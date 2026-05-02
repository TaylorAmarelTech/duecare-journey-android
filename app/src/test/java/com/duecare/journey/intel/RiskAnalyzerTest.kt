package com.duecare.journey.intel

import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAnalyzerTest {

    private val analyzer = RiskAnalyzer()

    private fun entry(
        title: String,
        body: String,
        id: String = "e1",
        ts: Long = 1L,
        stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
    ) = JournalEntry(
        id = id,
        timestampMillis = ts,
        stage = stage,
        kind = EntryKind.NOTE,
        title = title,
        body = body,
    )

    @Test
    fun analyze_returns_empty_for_benign_entry() {
        val risks = analyzer.analyze(entry("Lunch", "I had soup today"))
        assertTrue(risks.isEmpty())
    }

    @Test
    fun analyze_returns_risks_for_passport_withholding() {
        val e = entry(
            "Recruiter took passport",
            "She said the passport will be kept by the agency for safekeeping",
        )
        val risks = analyzer.analyze(e)
        assertTrue("Expected at least one risk", risks.isNotEmpty())
        assertTrue(risks.any { it.ruleId == "passport-withholding" })
    }

    @Test
    fun analyze_carries_source_entry_metadata() {
        val e = entry(
            "Test",
            "kept passport for safekeeping with the employer",
            id = "abc",
        )
        val risks = analyzer.analyze(e)
        assertTrue(risks.isNotEmpty())
        risks.forEach {
            assertEquals("abc", it.sourceEntryId)
            assertEquals("Test", it.sourceEntryTitle)
        }
    }

    @Test
    fun analyzeAll_orders_by_severity_then_recency() {
        val critical = entry(
            "passport gone", "the passport is held by my employer for safekeeping",
            id = "old", ts = 1_000L,
        )
        val high = entry(
            "fee", "Paid 50,000 training fee", id = "new", ts = 2_000L,
        )
        val all = analyzer.analyzeAll(listOf(critical, high))
        assertTrue("Expected at least 2 risks", all.size >= 2)
        assertEquals(DomainKnowledge.GrepRules.Severity.CRITICAL, all.first().severity)
    }

    @Test
    fun iloIndicatorHistogram_counts_per_indicator() {
        val e1 = entry("a", "kept passport for safekeeping with the employer", id = "a")
        val e2 = entry("b", "passport is held by sponsor for safekeeping", id = "b")
        val all = analyzer.analyzeAll(listOf(e1, e2))
        val histogram = analyzer.iloIndicatorHistogram(all)
        // Both entries fire ILO indicator #8 (passport withholding)
        assertTrue("Expected indicator #8 to appear at least twice",
            (histogram[8] ?: 0) >= 2)
    }
}
