package com.duecare.journey.intel

import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBuilderTest {

    private val builder = TimelineBuilder(RiskAnalyzer())

    private fun entry(
        id: String, ts: Long, stage: JourneyStage, body: String = "neutral text",
    ) = JournalEntry(
        id = id, timestampMillis = ts, stage = stage,
        kind = EntryKind.NOTE, title = "Title $id", body = body,
    )

    @Test
    fun build_orders_events_chronologically() {
        val events = listOf(
            entry("c", 3_000L, JourneyStage.EMPLOYED),
            entry("a", 1_000L, JourneyStage.PRE_DEPARTURE),
            entry("b", 2_000L, JourneyStage.IN_TRANSIT),
        )
        val tl = builder.build(events)
        assertEquals(listOf("a", "b", "c"), tl.events.map { it.id })
    }

    @Test
    fun build_groups_by_stage_in_chronological_first_event_order() {
        val events = listOf(
            entry("a", 1_000L, JourneyStage.PRE_DEPARTURE),
            entry("b", 2_000L, JourneyStage.IN_TRANSIT),
            entry("c", 3_000L, JourneyStage.EMPLOYED),
        )
        val tl = builder.build(events)
        assertEquals(
            listOf(
                JourneyStage.PRE_DEPARTURE,
                JourneyStage.IN_TRANSIT,
                JourneyStage.EMPLOYED,
            ),
            tl.byStage.map { it.stage },
        )
    }

    @Test
    fun build_counts_critical_risks_separately_from_total() {
        val events = listOf(
            entry("crit", 1_000L, JourneyStage.PRE_DEPARTURE,
                body = "kept passport for safekeeping with the employer"),
            entry("med", 2_000L, JourneyStage.PRE_DEPARTURE,
                body = "12 hours per day with no rest day"),
        )
        val tl = builder.build(events)
        assertTrue("Expected at least one critical risk", tl.criticalRisks >= 1)
        assertTrue("Total risks must include critical + others",
            tl.totalRisks >= tl.criticalRisks)
    }

    @Test
    fun build_returns_empty_timeline_for_no_entries() {
        val tl = builder.build(emptyList())
        assertEquals(0, tl.totalEvents)
        assertEquals(0, tl.totalRisks)
        assertEquals(null, tl.firstEventMillis)
        assertEquals(null, tl.lastEventMillis)
    }

    @Test
    fun build_records_first_and_last_event_times() {
        val events = listOf(
            entry("a", 1_000L, JourneyStage.PRE_DEPARTURE),
            entry("b", 5_000L, JourneyStage.EMPLOYED),
        )
        val tl = builder.build(events)
        assertEquals(1_000L, tl.firstEventMillis)
        assertEquals(5_000L, tl.lastEventMillis)
    }
}
