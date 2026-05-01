package com.duecare.journey.journal.sample

import com.duecare.journey.journal.AssessmentSource
import com.duecare.journey.journal.AssessmentTarget
import com.duecare.journey.journal.AssessmentVerdict
import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.LegalAssessment
import com.duecare.journey.journal.LicenseStatus
import com.duecare.journey.journal.Party
import com.duecare.journey.journal.PartyKind
import com.duecare.journey.journal.PaymentMethod
import com.duecare.journey.journal.RefundClaim
import com.duecare.journey.journal.RefundStatus

/**
 * Hand-crafted sample data for the v0.2 flagship Journal screen.
 * Renders a realistic worker timeline so judges + reviewers can
 * see the harm-reduction flow in one screen — without waiting for
 * v1 MVP's actual journal-entry creation UI.
 *
 * Worker: composite, labeled in the writeup. PH→HK domestic-worker
 * corridor. Has just paid a fee that the harness flags as illegal
 * under POEA MC 14-2017; the screen shows both the legal assessment
 * and a "Start refund claim" call-to-action — the worker chooses.
 *
 * Removed in v1 MVP once the worker can record their own entries.
 */
object SampleData {

    val pacificCoastManpower = Party(
        id = "p_pcm",
        kind = PartyKind.RECRUITMENT_AGENCY,
        name = "Pacific Coast Manpower Inc.",
        country = "PH",
        licenseNumber = "POEA-1234-5678",
        licenseStatus = LicenseStatus.LICENSED_VERIFIED,
        notes = "Tier-1 PRA based in Manila; deploys to HK domestic.",
    )

    val poeaAirb = Party(
        id = "p_poea_airb",
        kind = PartyKind.REGULATOR,
        name = "POEA Anti-Illegal Recruitment Branch",
        country = "PH",
        contactPhone = "+63-2-8721-1144",
        notes = "Files illegal-recruitment + refund-claim cases " +
            "for OFWs.",
    )

    val mfmwHk = Party(
        id = "p_mfmw",
        kind = PartyKind.NGO,
        name = "Mission for Migrant Workers Hong Kong",
        country = "HK",
        contactPhone = "+852-2522-8264",
        notes = "Hotline + drop-in centre for domestic helpers in HK.",
    )

    val sampleParties = listOf(pacificCoastManpower, poeaAirb, mfmwHk)

    val trainingFeePayment = FeePayment(
        id = "fp_001",
        paidAtMillis = 1745020800000L,           // 2026-04-19 08:00 UTC
        amountMinorUnits = 5_000_000L,           // ₱50,000.00 in centavos
        currency = "PHP",
        paidToId = pacificCoastManpower.id,
        purposeLabel = "training fees",
        purposeAsClaimedByPayer = "pre-departure orientation training (mandatory per agency policy)",
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        receiptAttachmentId = null,              // worker hasn't photographed yet
        contractClauseAttachmentId = null,
        workerNotes = "Recruiter said this is required before they release my visa. I paid via BPI transfer to the agency's listed account.",
        legalAssessmentId = "la_001",
        refundClaimId = null,                    // not pursued yet
        stage = JourneyStage.PRE_DEPARTURE,
    )

    val trainingFeeAssessment = LegalAssessment(
        id = "la_001",
        targetKind = AssessmentTarget.FEE_PAYMENT,
        targetId = trainingFeePayment.id,
        assessedAtMillis = 1745020900000L,
        harnessVerdict = AssessmentVerdict.ILLEGAL,
        harnessReasoning = "POEA Memorandum Circular 14-2017 §3 " +
            "establishes a ZERO placement fee for Filipino household " +
            "service workers (HSWs) deployed to Hong Kong. \"Training " +
            "fees\" are explicitly named as a prohibited camouflage " +
            "label — they fall under the same prohibition as " +
            "placement fees regardless of how they're framed. ILO " +
            "C181 Art. 7 governs internationally. Polaris's 2024 " +
            "Recruitment Fraud Typology lists \"training fee\" as " +
            "the most common camouflage. The agency may be licensed " +
            "(POEA-1234-5678 is a real license number format) but a " +
            "license does not authorize charging this fee.",
        controllingStatute = "POEA MC 14-2017 §3",
        controllingConvention = "ILO C181 Art. 7",
        grepHitsJoined = "fee_camouflage_training,zero_fee_corridor_violation",
        ragDocsJoined = "poea_mc_14_2017,ilo_c181_art7,polaris_recruitment_2024",
        workerVerdict = AssessmentVerdict.ILLEGAL,    // worker agrees
        workerNotes = null,
        source = AssessmentSource.HARNESS_AUTO,
    )

    val sampleJournalEntries = listOf(
        JournalEntry(
            id = "je_001",
            timestampMillis = 1744934400000L,    // 2026-04-18 08:00 UTC
            stage = JourneyStage.PRE_DEPARTURE,
            kind = EntryKind.MESSAGE,
            title = "Recruiter WhatsApp: \"You need to pay training fee first\"",
            body = "Maam, before we can release your visa we need you " +
                "to pay the ₱50,000 training fee. This is mandatory " +
                "per agency policy. Once paid we will book your " +
                "flight to HK.",
            parties = listOf(pacificCoastManpower.id),
            taggedConcerns = listOf("FEE_VIOLATION"),
        ),
        JournalEntry(
            id = "je_002",
            timestampMillis = 1745020800000L,    // 2026-04-19 08:00 UTC
            stage = JourneyStage.PRE_DEPARTURE,
            kind = EntryKind.EXPENSE,
            title = "Paid ₱50,000 training fee via BPI transfer",
            body = "Transferred ₱50,000.00 to Pacific Coast Manpower's " +
                "listed BPI account. Reference: BPI-PH-XXXX. Receipt " +
                "screenshot saved to Photos.",
            parties = listOf(pacificCoastManpower.id),
            taggedConcerns = listOf("FEE_VIOLATION", "FEE_CAMOUFLAGE"),
            grepHits = listOf("fee_camouflage_training"),
        ),
    )
}
