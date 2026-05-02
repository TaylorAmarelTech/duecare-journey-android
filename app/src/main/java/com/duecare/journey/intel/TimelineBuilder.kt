package com.duecare.journey.intel

import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces a journal corpus to a structured chronological timeline that
 * an NGO caseworker (or the worker themselves) can scan in seconds.
 *
 * Output is a list of [TimelineEvent]s, ordered oldest→newest, grouped
 * by stage of journey, with risk-tagged events visually distinguishable.
 *
 * Pure function — no I/O.
 */
@Singleton
class TimelineBuilder @Inject constructor(
    private val riskAnalyzer: RiskAnalyzer,
) {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    data class TimelineEvent(
        val id: String,
        val timestampMillis: Long,
        val displayDate: String,
        val stage: JourneyStage,
        val title: String,
        val body: String,
        val risks: List<RiskAnalyzer.Risk>,
    ) {
        val highestSeverity: DomainKnowledge.GrepRules.Severity? =
            risks.minByOrNull { it.severity.ordinal }?.severity
    }

    data class StageBucket(
        val stage: JourneyStage,
        val stageLabel: String,
        val events: List<TimelineEvent>,
        val firstEventMillis: Long,
        val lastEventMillis: Long,
    )

    data class Timeline(
        val events: List<TimelineEvent>,
        val byStage: List<StageBucket>,
        val totalEvents: Int,
        val totalRisks: Int,
        val criticalRisks: Int,
        val firstEventMillis: Long?,
        val lastEventMillis: Long?,
    )

    fun build(entries: List<JournalEntry>): Timeline {
        val sorted = entries.sortedBy { it.timestampMillis }
        val events = sorted.map { entry ->
            val risks = riskAnalyzer.analyze(entry)
            TimelineEvent(
                id = entry.id,
                timestampMillis = entry.timestampMillis,
                displayDate = dateFmt.format(Date(entry.timestampMillis)),
                stage = entry.stage,
                title = entry.title,
                body = entry.body,
                risks = risks,
            )
        }
        val byStage = events.groupBy { it.stage }
            .map { (stage, evts) ->
                StageBucket(
                    stage = stage,
                    stageLabel = stageLabel(stage),
                    events = evts,
                    firstEventMillis = evts.minOf { it.timestampMillis },
                    lastEventMillis = evts.maxOf { it.timestampMillis },
                )
            }
            .sortedBy { it.firstEventMillis }
        val totalRisks = events.sumOf { it.risks.size }
        val criticalRisks = events.sumOf { evt ->
            evt.risks.count { it.severity == DomainKnowledge.GrepRules.Severity.CRITICAL }
        }
        return Timeline(
            events = events,
            byStage = byStage,
            totalEvents = events.size,
            totalRisks = totalRisks,
            criticalRisks = criticalRisks,
            firstEventMillis = events.firstOrNull()?.timestampMillis,
            lastEventMillis = events.lastOrNull()?.timestampMillis,
        )
    }

    private fun stageLabel(stage: JourneyStage): String = when (stage) {
        JourneyStage.PRE_DEPARTURE -> "Pre-departure"
        JourneyStage.IN_TRANSIT -> "In transit"
        JourneyStage.ARRIVED -> "Arrived in destination country"
        JourneyStage.EMPLOYED -> "Employed"
        JourneyStage.BETWEEN_EMPLOYERS -> "Between employers (in country)"
        JourneyStage.EXIT -> "Exit / repatriation"
    }
}
