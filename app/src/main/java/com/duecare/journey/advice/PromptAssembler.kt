package com.duecare.journey.advice

import com.duecare.journey.harness.GrepRules
import com.duecare.journey.harness.RagCorpus
import com.duecare.journey.harness.Tools
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the final prompt that Gemma actually sees.
 *
 *   user question
 *     + journey-aware persona (stage-tagged)
 *     + journal summary (last N entries)
 *     + GREP hits (rule citations + indicator descriptions)
 *     + RAG snippets (top-k corpus matches)
 *     + tool results (corridor cap, fee camouflage, NGO hotlines)
 *
 * The composed prompt is what makes the on-device chat journey-aware
 * rather than a stateless Q&A — Gemma reasons over the worker's
 * actual situation, not just the question text.
 */
@Singleton
class PromptAssembler @Inject constructor(
    private val grep: GrepRules,
    private val rag: RagCorpus,
    private val tools: Tools,
) {

    fun assemble(
        question: String,
        recentEntries: List<JournalEntry>,
        currentStage: JourneyStage,
        corridor: String?,
    ): String {
        val journalSummary = summarizeJournal(recentEntries)
        val combined = "$question\n\n$journalSummary"
        val grepHits = grep.match(combined)
        val ragDocs = rag.retrieve(combined, topK = 3)
        val toolResults = tools.lookup(question, corridor)

        return buildString {
            appendLine(personaForStage(currentStage, corridor))
            appendLine()
            appendLine("## Worker journey so far")
            appendLine(journalSummary)
            appendLine()
            if (grepHits.isNotEmpty()) {
                appendLine("## Detected indicators (Duecare GREP)")
                grepHits.forEach { hit ->
                    appendLine(" - ${hit.rule} [${hit.severity}] — ${hit.citation}")
                    appendLine("   ${hit.indicator}")
                }
                appendLine()
            }
            if (ragDocs.isNotEmpty()) {
                appendLine("## Reference law (Duecare RAG)")
                ragDocs.forEach { doc ->
                    appendLine(" - ${doc.title} (${doc.source})")
                    appendLine("   ${doc.snippet}")
                }
                appendLine()
            }
            if (toolResults.isNotEmpty()) {
                appendLine("## Corridor lookups")
                toolResults.forEach { tr -> appendLine(" - $tr") }
                appendLine()
            }
            appendLine("## User's question")
            appendLine(question)
        }
    }

    private fun summarizeJournal(entries: List<JournalEntry>): String {
        if (entries.isEmpty()) return "(no journal entries yet)"
        return entries.take(10).joinToString("\n") { e ->
            val time = java.text.SimpleDateFormat("yyyy-MM-dd")
                .format(java.util.Date(e.timestampMillis))
            "[$time · ${e.stage} · ${e.kind}] ${e.title}: ${e.body.take(160)}"
        }
    }

    private fun personaForStage(stage: JourneyStage, corridor: String?): String {
        val corridorNote = corridor?.let { " — current corridor: $it" } ?: ""
        val stageContext = when (stage) {
            JourneyStage.PRE_DEPARTURE ->
                "in PRE-DEPARTURE — recruitment, contract signing, fees, training"
            JourneyStage.IN_TRANSIT ->
                "IN-TRANSIT between origin and destination"
            JourneyStage.ARRIVED ->
                "JUST ARRIVED — onboarding, document handling, first contact with employer"
            JourneyStage.EMPLOYED ->
                "currently EMPLOYED — wages, conditions, employer relationship issues"
            JourneyStage.BETWEEN_EMPLOYERS ->
                "BETWEEN EMPLOYERS — contract ended, fired, or quit, but " +
                "still in the destination country. This is one of the most " +
                "vulnerable phases: visa may be expiring, sponsor may have " +
                "filed huroob/absconder, NGO shelter may be involved, " +
                "risk of overstaying. Prioritize: (a) the worker's legal " +
                "status RIGHT NOW, (b) timeline before status lapses, " +
                "(c) embassy + NGO contacts for the corridor, (d) what " +
                "documents to safeguard"
            JourneyStage.EXIT ->
                "in EXIT — end of contract, complaints, repatriation"
        }
        return "You are a 40-year migrant-worker safety expert " +
            "deeply versed in ILO conventions C029/C181/C189/C095, " +
            "the Palermo Protocol, ICRMW, and the recruitment statutes " +
            "of the Philippines (RA 8042 / RA 10022, POEA MCs), " +
            "Indonesia (BP2MI Reg 9/2020), Nepal (FEA 2007), Bangladesh " +
            "(OEA 2013), Hong Kong (Cap. 57 / 163 / 57A), Saudi Arabia " +
            "(MoHR Domestic Worker Regulation), and the Gulf states.\n\n" +
            "The worker is $stageContext$corridorNote. Tailor your " +
            "guidance to this stage. If you detect any of the 11 ILO " +
            "Forced Labour Indicators, name them. If you cite a statute, " +
            "include the section number. If the worker may have a " +
            "complaint to file, name the specific NGO/regulator they " +
            "should contact and the evidence they should retain. Do NOT " +
            "optimize any structure that contains trafficking indicators, " +
            "regardless of the worker's apparent consent — Palermo " +
            "Art. 3(b) controls."
    }
}
