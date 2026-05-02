package com.duecare.journey.intel

import com.duecare.journey.intel.DomainKnowledge.CorridorKnowledge
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.Party
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Aggregates fee data from two sources:
 *
 *   1. Structured [FeePayment] rows (the v2 schema, populated when the
 *      worker uses the structured fee-recording flow).
 *   2. Unstructured journal entries — extracted via simple regex over
 *      title + body, so a worker who jotted "Paid agency 50,000 PHP for
 *      training" still gets aggregated.
 *
 * Output is grouped by recipient party (or recipient name string for
 * extracted entries), with totals per currency, with each line tagged
 * legal/illegal based on the corridor's published fee cap.
 *
 * This is the data NGOs explicitly want: "who got how much, for what,
 * when". Pure function — no I/O.
 */
@Singleton
class FeeAggregator @Inject constructor() {

    data class FeeLine(
        val sourceKind: SourceKind,
        val recipientName: String,
        val recipientPartyId: String?,
        val recipientPartyKind: String?,
        val purposeLabel: String,
        val purposeAsClaimed: String?,
        val amountMinorUnits: Long,
        val currency: String,
        val displayAmount: String,
        val paidAtMillis: Long,
        val sourceEntryId: String?,
        /** Set when this line came from a structured FeePayment row.
         *  Null when extracted from journal-entry text. Used by the
         *  Reports tab's "Start refund claim" affordance — only
         *  structured rows can be claim sources because we need a
         *  recorded FeePayment to attach the LegalAssessment to. */
        val feePaymentId: String?,
        val isProbablyIllegal: Boolean,
        val illegalityReason: String?,
    )

    data class CurrencyTotal(
        val currency: String,
        val totalMinorUnits: Long,
        val displayTotal: String,
    )

    data class FeeReport(
        val lines: List<FeeLine>,
        val totalsByCurrency: List<CurrencyTotal>,
        val totalsByRecipient: Map<String, List<CurrencyTotal>>,
        val illegalLines: List<FeeLine>,
        val illegalTotalsByCurrency: List<CurrencyTotal>,
    )

    enum class SourceKind { STRUCTURED, EXTRACTED_FROM_JOURNAL }

    fun build(
        structured: List<FeePayment>,
        parties: List<Party>,
        journalEntries: List<JournalEntry>,
        corridorCode: String?,
    ): FeeReport {
        val partyById = parties.associateBy { it.id }
        val corridor = CorridorKnowledge.byCode(corridorCode)

        val structuredLines = structured.map { p ->
            val party = partyById[p.paidToId]
            val (illegal, reason) = checkIllegality(
                amountMinorUnits = p.amountMinorUnits,
                currency = p.currency,
                purposeLabel = p.purposeLabel,
                corridor = corridor,
            )
            FeeLine(
                sourceKind = SourceKind.STRUCTURED,
                recipientName = party?.name ?: "(unknown party ${p.paidToId.take(6)})",
                recipientPartyId = p.paidToId,
                recipientPartyKind = party?.kind?.name,
                purposeLabel = p.purposeLabel,
                purposeAsClaimed = p.purposeAsClaimedByPayer,
                amountMinorUnits = p.amountMinorUnits,
                currency = p.currency,
                displayAmount = formatAmount(p.amountMinorUnits, p.currency),
                paidAtMillis = p.paidAtMillis,
                sourceEntryId = null,
                feePaymentId = p.id,
                isProbablyIllegal = illegal,
                illegalityReason = reason,
            )
        }

        val extractedLines = journalEntries.flatMap { entry ->
            extractFeesFromText(entry, corridor)
        }

        val all = (structuredLines + extractedLines).sortedByDescending { it.paidAtMillis }
        val totalsByCurrency = all.groupBy { it.currency }
            .map { (cur, ls) ->
                val sum = ls.sumOf { it.amountMinorUnits }
                CurrencyTotal(cur, sum, formatAmount(sum, cur))
            }
            .sortedByDescending { it.totalMinorUnits }
        val totalsByRecipient = all.groupBy { it.recipientName }
            .mapValues { (_, ls) ->
                ls.groupBy { it.currency }.map { (cur, sub) ->
                    val sum = sub.sumOf { it.amountMinorUnits }
                    CurrencyTotal(cur, sum, formatAmount(sum, cur))
                }
            }
        val illegal = all.filter { it.isProbablyIllegal }
        val illegalTotals = illegal.groupBy { it.currency }
            .map { (cur, ls) ->
                val sum = ls.sumOf { it.amountMinorUnits }
                CurrencyTotal(cur, sum, formatAmount(sum, cur))
            }
            .sortedByDescending { it.totalMinorUnits }
        return FeeReport(
            lines = all,
            totalsByCurrency = totalsByCurrency,
            totalsByRecipient = totalsByRecipient,
            illegalLines = illegal,
            illegalTotalsByCurrency = illegalTotals,
        )
    }

    /** Crude regex extraction. Matches numbers next to a currency
     *  symbol or ISO code, and a verb implying payment. Designed
     *  conservatively — extracts even when amount + currency are
     *  separated by a noun. False-positive cost is low (NGO can
     *  ignore); false-negative cost is high (lost evidence).
     */
    private fun extractFeesFromText(
        entry: JournalEntry,
        corridor: CorridorKnowledge.Corridor?,
    ): List<FeeLine> {
        val text = "${entry.title}\n${entry.body}"
        val out = mutableListOf<FeeLine>()
        for (m in CURRENCY_AMOUNT.findAll(text)) {
            val (curRaw, amountRaw) = parseCurrencyAndAmount(m.value) ?: continue
            val amountMinor = (amountRaw * 100).toLong()
            val purposeLabel = inferPurposeLabel(text)
            val recipient = inferRecipient(entry)
            val (illegal, reason) = checkIllegality(amountMinor, curRaw, purposeLabel, corridor)
            out += FeeLine(
                sourceKind = SourceKind.EXTRACTED_FROM_JOURNAL,
                recipientName = recipient,
                recipientPartyId = null,
                recipientPartyKind = null,
                purposeLabel = purposeLabel,
                purposeAsClaimed = null,
                amountMinorUnits = amountMinor,
                currency = curRaw,
                displayAmount = formatAmount(amountMinor, curRaw),
                paidAtMillis = entry.timestampMillis,
                sourceEntryId = entry.id,
                feePaymentId = null,
                isProbablyIllegal = illegal,
                illegalityReason = reason,
            )
        }
        return out
    }

    private fun inferPurposeLabel(text: String): String {
        val lower = text.lowercase()
        return when {
            "training fee" in lower -> "training fee"
            "medical" in lower -> "medical fee"
            "processing" in lower -> "processing fee"
            "placement" in lower -> "placement fee"
            "service" in lower -> "service fee"
            "loan" in lower -> "loan disbursement"
            "deposit" in lower -> "deposit"
            "passport" in lower -> "passport / document fee"
            else -> "(unspecified)"
        }
    }

    private fun inferRecipient(entry: JournalEntry): String {
        if (entry.parties.isNotEmpty()) return entry.parties.first()
        return "(named in entry: ${entry.title.take(60)})"
    }

    private fun parseCurrencyAndAmount(raw: String): Pair<String, Double>? {
        val trimmed = raw.trim()
        // Try ISO code prefix: "PHP 50,000" or "USD 1,800"
        for (code in CURRENCIES) {
            if (trimmed.startsWith(code, ignoreCase = true) ||
                trimmed.endsWith(code, ignoreCase = true)) {
                val amount = trimmed.replace(code, "", ignoreCase = true)
                    .replace(",", "")
                    .replace("\\s".toRegex(), "")
                    .toDoubleOrNull() ?: return null
                return code.uppercase() to amount
            }
        }
        // Try symbol prefix: ₱50,000 or $1,800 or HK$5,000
        if (trimmed.startsWith("₱")) return "PHP" to numericPart(trimmed)
        if (trimmed.startsWith("HK$")) return "HKD" to numericPart(trimmed)
        if (trimmed.startsWith("S$")) return "SGD" to numericPart(trimmed)
        if (trimmed.startsWith("$")) return "USD" to numericPart(trimmed)
        if (trimmed.startsWith("RM")) return "MYR" to numericPart(trimmed)
        if (trimmed.startsWith("Rs.") || trimmed.startsWith("Rs ")) return "INR" to numericPart(trimmed)
        if (trimmed.startsWith("৳")) return "BDT" to numericPart(trimmed)
        if (trimmed.startsWith("₨")) return "NPR" to numericPart(trimmed)
        if (trimmed.startsWith("Rp")) return "IDR" to numericPart(trimmed)
        if (trimmed.startsWith("﷼") || trimmed.startsWith("SAR ")) return "SAR" to numericPart(trimmed)
        if (trimmed.startsWith("AED ")) return "AED" to numericPart(trimmed)
        return null
    }

    private fun numericPart(s: String): Double = s
        .replace("[^0-9.]".toRegex(), "")
        .toDoubleOrNull() ?: 0.0

    private fun checkIllegality(
        amountMinorUnits: Long,
        currency: String,
        purposeLabel: String,
        corridor: CorridorKnowledge.Corridor?,
    ): Pair<Boolean, String?> {
        if (corridor?.placementFeeCapUsd == null) return false to null
        val cap = corridor.placementFeeCapUsd
        val usd = roughUsdEquivalent(amountMinorUnits, currency)
        // If we can't convert, fall back to comparing major units; we'll
        // flag any fee at a recruiter-shaped purpose label
        if (cap == 0.0) {
            // Zero-fee corridor — any payment for placement is illegal
            val zerofeeKeywords = listOf("placement", "training", "processing",
                "service", "recruitment", "agency", "medical")
            if (zerofeeKeywords.any { it in purposeLabel.lowercase() }) {
                return true to "Corridor caps placement fees at $0; this " +
                    "fee is likely recoverable. ${corridor.placementFeeNote}"
            }
        }
        if (usd != null && usd > cap * 1.05) {
            return true to "Exceeds ${corridor.code} fee cap of " +
                "USD ${"%.0f".format(cap)} by approx USD " +
                "${"%.0f".format(usd - cap)}. ${corridor.placementFeeNote}"
        }
        return false to null
    }

    /** Very rough — uses static mid-2026 rates. Good enough for an
     *  ILLEGAL/LEGAL flag in the report; a precise FX conversion is
     *  not the report's job. */
    private fun roughUsdEquivalent(amountMinorUnits: Long, currency: String): Double? {
        val rate = USD_PER_UNIT[currency.uppercase()] ?: return null
        return (amountMinorUnits / 100.0) * rate
    }

    private fun formatAmount(amountMinorUnits: Long, currency: String): String {
        return try {
            val cur = Currency.getInstance(currency.uppercase())
            val nf = NumberFormat.getCurrencyInstance(Locale.US).apply {
                this.currency = cur
                minimumFractionDigits = if (cur.defaultFractionDigits == 0) 0 else 2
                maximumFractionDigits = cur.defaultFractionDigits
            }
            val major = amountMinorUnits / 100.0
            nf.format(major)
        } catch (_: Throwable) {
            val major = amountMinorUnits / 100.0
            "${currency.uppercase()} ${"%,.2f".format(major)}"
        }
    }

    private companion object {
        val CURRENCY_AMOUNT = Regex(
            "(?:[₱$৳₨﷼]|HK\\$|S\\$|RM|Rs\\.?|Rp|SAR|AED|USD|PHP|HKD|SGD|IDR|" +
                "INR|NPR|BDT|MYR|THB|VND)\\s?\\d[\\d,]*(?:\\.\\d+)?|" +
                "\\d[\\d,]*(?:\\.\\d+)?\\s?(?:USD|PHP|HKD|SGD|IDR|INR|NPR|BDT|MYR|THB|VND|SAR|AED)",
            RegexOption.IGNORE_CASE,
        )
        val CURRENCIES = listOf("USD", "PHP", "HKD", "SGD", "IDR", "INR",
            "NPR", "BDT", "MYR", "THB", "VND", "SAR", "AED")
        // Extremely rough USD-per-unit conversion (mid-2026)
        val USD_PER_UNIT: Map<String, Double> = mapOf(
            "USD" to 1.0,
            "PHP" to 1.0 / 56.0,
            "HKD" to 1.0 / 7.85,
            "SGD" to 1.0 / 1.35,
            "IDR" to 1.0 / 16_000.0,
            "INR" to 1.0 / 84.0,
            "NPR" to 1.0 / 134.0,
            "BDT" to 1.0 / 119.0,
            "MYR" to 1.0 / 4.7,
            "THB" to 1.0 / 35.5,
            "VND" to 1.0 / 25_000.0,
            "SAR" to 1.0 / 3.75,
            "AED" to 1.0 / 3.67,
        )
    }
}
