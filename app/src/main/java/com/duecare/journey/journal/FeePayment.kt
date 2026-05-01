package com.duecare.journey.journal

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A specific kind of journal entry: money exchanged hands. First-class
 * because fee payments are the central trafficking-evidence object —
 * every recovery / refund-claim flow keys off them.
 *
 * Linked to:
 *   - the Party who received the money (FK)
 *   - an optional LegalAssessment (FK) — auto-attached at creation time
 *     by running the harness deterministic rules against the payment
 *     details
 *   - an optional RefundClaim (FK) — created when the worker decides
 *     to pursue recovery
 *
 * Workflow:
 *   1. Worker records a payment (amount, recipient, purpose, receipt).
 *   2. Harness runs auto: legal cap lookup, fee-camouflage match, etc.
 *      Auto-creates a LegalAssessment with harnessVerdict.
 *   3. Worker reviews the assessment. If it's ILLEGAL, the UI surfaces
 *      a "Start refund claim" CTA. Worker chooses whether to proceed.
 *   4. If they tap it, a RefundClaim row is created and linked.
 */
@Entity(
    tableName = "fee_payments",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["paidToId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("paidToId"), Index("paidAtMillis")],
)
data class FeePayment(
    @PrimaryKey val id: String,                        // UUID
    val paidAtMillis: Long,
    val amountMinorUnits: Long,                        // e.g. centavos, sen — avoid float
    val currency: String,                              // ISO 4217 (PHP, IDR, USD, HKD)
    val paidToId: String,                              // FK -> Party.id
    val purposeLabel: String,                          // "training fee", "medical", "deposit", etc.
    val purposeAsClaimedByPayer: String? = null,       // recruiter's exact wording
    val paymentMethod: PaymentMethod,
    val receiptAttachmentId: String? = null,           // FK -> Attachment.id
    val contractClauseAttachmentId: String? = null,    // optional: photo of clause that named fee
    val workerNotes: String? = null,
    val legalAssessmentId: String? = null,             // FK -> LegalAssessment.id (lazy)
    val refundClaimId: String? = null,                 // FK -> RefundClaim.id (lazy)
    val stage: JourneyStage,
)

enum class PaymentMethod {
    CASH,
    BANK_TRANSFER,
    MOBILE_WALLET,        // GCash, OVO, eSewa, bKash, etc.
    REMITTANCE,           // Western Union, MoneyGram, Wise
    CRYPTO,               // FATF Recommendation 32 trigger
    SALARY_DEDUCTION,     // recruiter takes future wages — debt-bondage shape
    LOAN_DISBURSEMENT,    // a third-party loan is paid to the recruiter
    OTHER,
}
