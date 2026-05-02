package com.duecare.journey.journal

import com.duecare.journey.intel.DomainKnowledge.CorridorKnowledge
import com.duecare.journey.intel.StructuredFeeAssessor
import com.duecare.journey.onboarding.OnboardingPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.7: write-side wrapper for FeePaymentDao that auto-creates a
 * [LegalAssessment] when the fee is recoverable per the corridor cap.
 *
 * Read-side queries continue to go through [FeePaymentDao] directly —
 * this layer only exists for the write path so there's exactly one
 * place where "structured fee was added" leads to "harness assessed it".
 */
@Singleton
class FeePaymentRepository @Inject constructor(
    private val feeDao: FeePaymentDao,
    private val assessmentDao: LegalAssessmentDao,
    private val partyDao: PartyDao,
    private val onboarding: OnboardingPrefs,
    private val assessor: StructuredFeeAssessor,
) {

    fun observeAll(): Flow<List<FeePayment>> = feeDao.observeAll()

    suspend fun add(
        amountMinorUnits: Long,
        currency: String,
        paidToPartyId: String,
        purposeLabel: String,
        purposeAsClaimedByPayer: String? = null,
        paymentMethod: PaymentMethod,
        paidAtMillis: Long = System.currentTimeMillis(),
        stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
        receiptAttachmentId: String? = null,
        workerNotes: String? = null,
    ): AddResult {
        val payment = FeePayment(
            id = UUID.randomUUID().toString(),
            paidAtMillis = paidAtMillis,
            amountMinorUnits = amountMinorUnits,
            currency = currency.uppercase(),
            paidToId = paidToPartyId,
            purposeLabel = purposeLabel,
            purposeAsClaimedByPayer = purposeAsClaimedByPayer,
            paymentMethod = paymentMethod,
            receiptAttachmentId = receiptAttachmentId,
            workerNotes = workerNotes,
            stage = stage,
        )
        feeDao.upsert(payment)

        val corridor = onboarding.corridor.first()
        val assessment = assessor.assess(payment, corridor)
        if (assessment != null) {
            assessmentDao.insert(assessment)
            // Backlink the assessment ID onto the FeePayment row so
            // the Reports tab can render the verdict without a join.
            feeDao.upsert(payment.copy(legalAssessmentId = assessment.id))
        }
        return AddResult(payment, assessment)
    }

    /** Quick "create this party + add this fee" combo for the
     *  Add-fee dialog when the worker hasn't yet recorded the
     *  recipient as a Party. */
    suspend fun addWithNewParty(
        partyName: String,
        partyKind: PartyKind,
        partyCountry: String?,
        partyLicenseNumber: String?,
        amountMinorUnits: Long,
        currency: String,
        purposeLabel: String,
        purposeAsClaimedByPayer: String?,
        paymentMethod: PaymentMethod,
        paidAtMillis: Long = System.currentTimeMillis(),
        stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
        workerNotes: String? = null,
    ): AddResult {
        val party = Party(
            id = UUID.randomUUID().toString(),
            kind = partyKind,
            name = partyName,
            country = partyCountry,
            licenseNumber = partyLicenseNumber,
            licenseStatus = LicenseStatus.UNKNOWN,
        )
        partyDao.upsert(party)
        return add(
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            paidToPartyId = party.id,
            purposeLabel = purposeLabel,
            purposeAsClaimedByPayer = purposeAsClaimedByPayer,
            paymentMethod = paymentMethod,
            paidAtMillis = paidAtMillis,
            stage = stage,
            workerNotes = workerNotes,
        )
    }

    data class AddResult(
        val payment: FeePayment,
        val assessment: LegalAssessment?,
    )
}
