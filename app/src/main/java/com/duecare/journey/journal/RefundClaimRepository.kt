package com.duecare.journey.journal

import com.duecare.journey.intel.DomainKnowledge.CorridorKnowledge
import com.duecare.journey.onboarding.OnboardingPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.7: drives the "worker decided to pursue a refund" flow.
 *
 * Given a [FeePayment] flagged as ILLEGAL by the harness, builds a
 * draft [RefundClaim] with controlling statute, regulator name,
 * pre-filled cover letter, and a share-ready delivery message.
 *
 * The worker can then edit, file, or withdraw the claim. The harness
 * never auto-files — it only drafts.
 */
@Singleton
class RefundClaimRepository @Inject constructor(
    private val claimDao: RefundClaimDao,
    private val feeDao: FeePaymentDao,
    private val partyDao: PartyDao,
    private val assessmentDao: LegalAssessmentDao,
    private val onboarding: OnboardingPrefs,
) {

    fun observeAll(): Flow<List<RefundClaim>> = claimDao.observeAll()
    fun observeOpen(): Flow<List<RefundClaim>> = claimDao.observeOpen()

    suspend fun byId(id: String): RefundClaim? = claimDao.byId(id)

    /** Build a draft claim for the given payment. Idempotent — if a
     *  claim already exists for this payment, returns the existing one
     *  (the caller can navigate to it instead of creating a duplicate). */
    suspend fun draftFor(paymentId: String): RefundClaim? {
        val payment = feeDao.byId(paymentId) ?: return null
        if (payment.refundClaimId != null) {
            val existing = claimDao.byId(payment.refundClaimId)
            if (existing != null) return existing
        }
        val recipient = partyDao.byId(payment.paidToId)
        val corridorCode = onboarding.corridor.first()
        val corridor = CorridorKnowledge.byCode(corridorCode)
        val assessment = assessmentDao.latest(AssessmentTarget.FEE_PAYMENT, paymentId)

        val claim = RefundClaim(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            status = RefundStatus.DRAFT,
            filedWithRegulatorId = null,
            amountClaimedMinorUnits = payment.amountMinorUnits,
            currency = payment.currency,
            coverLetterText = buildCoverLetter(payment, recipient, corridor, assessment),
            draftDeliveryMessage = buildDeliveryMessage(corridor),
        )
        claimDao.upsert(claim)
        feeDao.upsert(payment.copy(refundClaimId = claim.id))
        return claim
    }

    suspend fun updateNotes(id: String, notes: String) {
        val existing = claimDao.byId(id) ?: return
        claimDao.update(existing.copy(workerNotes = notes))
    }

    suspend fun markFiled(id: String, caseNumber: String? = null) {
        claimDao.markFiled(
            id = id,
            status = RefundStatus.FILED,
            filedAtMillis = System.currentTimeMillis(),
            caseNumber = caseNumber,
        )
    }

    suspend fun withdraw(id: String) {
        val existing = claimDao.byId(id) ?: return
        claimDao.update(existing.copy(status = RefundStatus.WITHDRAWN))
    }

    suspend fun markRecovered(id: String, amountMinorUnits: Long, partial: Boolean = false) {
        claimDao.markRecovered(
            id = id,
            status = if (partial) RefundStatus.PARTIALLY_RECOVERED else RefundStatus.GRANTED,
            amount = amountMinorUnits,
            at = System.currentTimeMillis(),
        )
    }

    private fun buildCoverLetter(
        payment: FeePayment,
        recipient: Party?,
        corridor: CorridorKnowledge.Corridor?,
        assessment: LegalAssessment?,
    ): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(payment.paidAtMillis))
        val amount = "${payment.currency} ${"%,.2f".format(payment.amountMinorUnits / 100.0)}"
        val recipientName = recipient?.name ?: "(unrecorded recipient)"
        val licenseLine = recipient?.licenseNumber?.let { "License: $it. " } ?: ""
        val statute = assessment?.controllingStatute
            ?: corridor?.let { inferDefaultStatute(it) }
            ?: "the controlling labour-recruitment statute for this corridor"
        val convention = assessment?.controllingConvention
            ?: "ILO C181 Art. 7 (Private Employment Agencies)"
        val regulatorName = corridor?.originRegulator?.name
            ?: "the origin-country labour regulator"

        return buildString {
            appendLine("To Whom It May Concern at $regulatorName,")
            appendLine()
            appendLine("I am writing to claim recovery of an illegal " +
                "placement-related fee that I paid in connection with my " +
                "${corridor?.let { "${it.originName} → ${it.destName}" } ?: "overseas employment"} " +
                "deployment.")
            appendLine()
            appendLine("Details of the payment:")
            appendLine("  • Date paid: $date")
            appendLine("  • Amount: $amount")
            appendLine("  • Paid to: $recipientName. $licenseLine")
            appendLine("  • Stated purpose: ${payment.purposeLabel}" +
                (payment.purposeAsClaimedByPayer?.let { " (\"$it\" per recipient)" } ?: ""))
            appendLine("  • Method: ${payment.paymentMethod.name.lowercase().replace('_', ' ')}")
            if (!payment.workerNotes.isNullOrBlank()) {
                appendLine("  • My note: ${payment.workerNotes}")
            }
            appendLine()
            appendLine("This payment is recoverable under $statute and is " +
                "an unfair recruitment practice under $convention.")
            assessment?.harnessReasoning?.let {
                appendLine()
                appendLine("Reasoning: $it")
            }
            appendLine()
            appendLine("I respectfully request:")
            appendLine("  1. Investigation of $recipientName under your " +
                "anti-illegal-recruitment authority.")
            appendLine("  2. Refund of the full $amount to me at the bank " +
                "details I will provide on filing.")
            appendLine("  3. Confirmation of the case number assigned to " +
                "this complaint.")
            appendLine()
            appendLine("I attach the receipt + the contract clause + a " +
                "chronological journal of my interactions with " +
                "$recipientName. I am available to provide additional " +
                "information as needed.")
            appendLine()
            appendLine("Respectfully,")
            appendLine("(your name, contact, date)")
        }.trim()
    }

    private fun buildDeliveryMessage(corridor: CorridorKnowledge.Corridor?): String {
        val regulator = corridor?.originRegulator
        val phone = regulator?.phone?.let { " · phone $it" } ?: ""
        return "Submitting an illegal-recruitment fee complaint per " +
            "${regulator?.name ?: "your authority"}'s public process. " +
            "Cover letter + supporting evidence attached.$phone"
    }

    private fun inferDefaultStatute(corridor: CorridorKnowledge.Corridor): String =
        when (corridor.code) {
            "PH-HK", "PH-SA" -> "POEA Memorandum Circular 14-2017 §3"
            "ID-HK", "ID-SG" -> "Indonesia Permenaker 9/2019"
            "NP-SA", "NP-QA" -> "Nepal Foreign Employment Act 2007 §22"
            "BD-SA" -> "Bangladesh BMET fee schedule"
            else -> "the controlling origin-country labour-recruitment statute"
        }
}
