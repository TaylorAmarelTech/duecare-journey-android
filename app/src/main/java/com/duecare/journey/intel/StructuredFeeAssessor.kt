package com.duecare.journey.intel

import com.duecare.journey.intel.DomainKnowledge.CorridorKnowledge
import com.duecare.journey.journal.AssessmentSource
import com.duecare.journey.journal.AssessmentTarget
import com.duecare.journey.journal.AssessmentVerdict
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.LegalAssessment
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.7: assesses a single structured [FeePayment] against the corridor
 * fee cap and emits a [LegalAssessment] if the fee is recoverable.
 * Single-purpose helper — stateless, fast, called from
 * [com.duecare.journey.journal.FeePaymentRepository] at write time.
 */
@Singleton
class StructuredFeeAssessor @Inject constructor() {

    /** Returns null when the harness has no opinion (no corridor set,
     *  or fee is within the published cap). Returns a populated
     *  [LegalAssessment] when the fee is likely recoverable. */
    fun assess(payment: FeePayment, corridorCode: String?): LegalAssessment? {
        val corridor = CorridorKnowledge.byCode(corridorCode) ?: return null
        val cap = corridor.placementFeeCapUsd ?: return null
        val usd = roughUsd(payment.amountMinorUnits, payment.currency)

        val isPlacementShaped = PLACEMENT_KEYWORDS.any {
            it in payment.purposeLabel.lowercase()
        } || PLACEMENT_KEYWORDS.any {
            it in (payment.purposeAsClaimedByPayer?.lowercase().orEmpty())
        }

        val (illegal, reason) = when {
            cap == 0.0 && isPlacementShaped -> true to
                "Corridor ${corridor.code} caps placement fees at \$0. " +
                    "${corridor.placementFeeNote} This payment of " +
                    "${formatMinor(payment.amountMinorUnits, payment.currency)} " +
                    "is recoverable in full from the recipient under " +
                    "origin-country regulations."
            usd != null && usd > cap * 1.05 -> true to
                "Corridor ${corridor.code} caps placement fees at " +
                    "USD ${"%.0f".format(cap)}. This payment is approx " +
                    "USD ${"%.0f".format(usd)} — exceeds cap by approx " +
                    "USD ${"%.0f".format(usd - cap)}. ${corridor.placementFeeNote}"
            else -> false to null
        }
        if (!illegal) return null

        return LegalAssessment(
            id = UUID.randomUUID().toString(),
            targetKind = AssessmentTarget.FEE_PAYMENT,
            targetId = payment.id,
            assessedAtMillis = System.currentTimeMillis(),
            harnessVerdict = AssessmentVerdict.ILLEGAL,
            harnessReasoning = reason!!,
            controllingStatute = inferStatute(corridor),
            controllingConvention = "ILO C181 Art. 7 (Private Employment Agencies)",
            grepHitsJoined = "fee_corridor_cap_violation",
            ragDocsJoined = "corridor_${corridor.code.lowercase()}",
            workerVerdict = AssessmentVerdict.ILLEGAL,
            source = AssessmentSource.HARNESS_AUTO,
        )
    }

    private fun inferStatute(corridor: CorridorKnowledge.Corridor): String? =
        when (corridor.code) {
            "PH-HK", "PH-SA" -> "POEA Memorandum Circular 14-2017 §3 (zero placement fee)"
            "ID-HK", "ID-SG" -> "Indonesia Permenaker 9/2019 (placement fee cap)"
            "NP-SA", "NP-QA" -> "Nepal Foreign Employment Act 2007 §22"
            "BD-SA" -> "Bangladesh BMET fee schedule (sector-specific)"
            else -> null
        }

    private fun roughUsd(minor: Long, currency: String): Double? {
        val rate = USD_PER_UNIT[currency.uppercase()] ?: return null
        return (minor / 100.0) * rate
    }

    private fun formatMinor(minor: Long, currency: String): String =
        "${currency.uppercase()} ${"%,.2f".format(minor / 100.0)}"

    private companion object {
        val PLACEMENT_KEYWORDS = listOf(
            "placement", "training", "processing", "service", "agency",
            "recruitment", "medical", "orientation", "deposit", "admin",
        )
        // Mid-2026 mid-rates. Used for the legality flag, not for any
        // user-facing exchange-rate display.
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
