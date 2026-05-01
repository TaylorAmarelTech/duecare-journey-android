package com.duecare.journey.export

import android.content.Context
import com.duecare.journey.inference.GemmaInferenceEngine
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JournalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a complaint-packet PDF from the worker's journal.
 *
 * Process:
 *   1. Pull all journal entries flagged with trafficking-indicator
 *      tags (fee, passport_retention, debt_bondage, wage_withholding, ...).
 *   2. Sort chronologically into a timeline.
 *   3. Ask Gemma (via [inference]) to draft a narrative cover letter.
 *   4. Identify the appropriate NGO/regulator from the corridor.
 *   5. Build a single PDF: cover narrative + timeline + evidence
 *      thumbnails + recommended recipient + draft delivery message.
 *   6. Return a content:// URI the worker can pass to a share intent.
 *
 * Privacy invariant: NEVER auto-sends. The PDF lands in the worker's
 * cache and they choose if/where to send it via the OS share sheet.
 */
@Singleton
class ComplaintPacketExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journal: JournalRepository,
    private val inference: GemmaInferenceEngine,
) {

    data class ComplaintPacket(
        val pdfFile: File,
        val timelineEntries: Int,
        val recommendedRecipient: String,
        val draftMessage: String,
    )

    suspend fun generate(): ComplaintPacket {
        val allEntries = journal.recent(limit = 1000)
        val concerning = allEntries.filter { it.taggedConcerns.isNotEmpty() }
        val corridor = journal.detectedCorridor() ?: "unknown"
        val recipient = recommendRecipient(corridor)
        val cover = draftCoverLetter(concerning, corridor)
        val draftMsg = draftDeliveryMessage(recipient)

        // TODO(v1): use Android PdfDocument API to render the cover +
        // chronological timeline + evidence thumbnails into a single
        // PDF written to context.cacheDir/complaint-packets/.
        val pdfFile = File(context.cacheDir, "complaint-packet.pdf")

        return ComplaintPacket(
            pdfFile = pdfFile,
            timelineEntries = concerning.size,
            recommendedRecipient = recipient,
            draftMessage = draftMsg,
        )
    }

    private fun recommendRecipient(corridor: String): String = when (corridor) {
        "PH-HK" -> "POEA Anti-Illegal Recruitment Branch +63-2-8721-1144 " +
            "/ Mission for Migrant Workers Hong Kong +852-2522-8264"
        "PH-SA" -> "POEA + Philippine Embassy Riyadh Migrant Worker Office"
        "ID-HK" -> "BP2MI Jakarta + Indonesian Migrant Workers Union HK"
        "NP-QA" -> "DoFE Kathmandu + Pravasi Nepali Coordination Committee Qatar"
        "BD-SA" -> "BMET Helpline +880-2-9357972 + WARBE"
        else -> "Your home-country embassy + IJM regional office"
    }

    private suspend fun draftCoverLetter(
        entries: List<JournalEntry>,
        corridor: String,
    ): String {
        if (entries.isEmpty()) return "(no flagged entries to summarize)"
        val timeline = entries.joinToString("\n") { e ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd")
                .format(java.util.Date(e.timestampMillis))
            "$date — ${e.title}: ${e.body.take(200)}"
        }
        val prompt = "Draft a one-page complaint cover letter for a " +
            "migrant worker on the $corridor corridor. Reference the " +
            "specific ILO indicators triggered, the relevant national " +
            "statute(s), and the events below in chronological order. " +
            "Use formal but accessible English; avoid legal jargon the " +
            "worker can't translate. End with a single specific ask.\n\n" +
            "Events:\n$timeline"
        return inference.generate(prompt, maxNewTokens = 1024)
    }

    private fun draftDeliveryMessage(recipient: String): String =
        "Dear $recipient,\n\n" +
        "I am attaching my migration journey complaint packet, " +
        "documenting events from pre-departure through my current " +
        "employment situation. I am requesting investigation under " +
        "the relevant labour and trafficking statutes cited in the " +
        "attached cover letter. Please confirm receipt and let me " +
        "know what additional information you require.\n\n" +
        "Thank you for your help."
}
