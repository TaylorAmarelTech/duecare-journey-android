package com.duecare.journey.journal

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The worker's pursuit of recovering one or more illegal fee payments.
 *
 * Created when the worker decides to act on a LegalAssessment of
 * ILLEGAL — they tap "Start refund claim" on a FeePayment, and a
 * draft RefundClaim is auto-populated with the controlling statute,
 * the regulator's contact info, the receipt attachment, and a draft
 * narrative cover letter.
 *
 * Multiple FeePayment rows can roll up into one RefundClaim if the
 * worker is pursuing several payments to the same recipient at once.
 *
 * Status moves through the workflow as the worker actually files,
 * waits, and (hopefully) recovers.
 */
@Entity(
    tableName = "refund_claims",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["filedWithRegulatorId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("filedWithRegulatorId"), Index("status")],
)
data class RefundClaim(
    @PrimaryKey val id: String,                              // UUID
    val createdAtMillis: Long,
    val status: RefundStatus = RefundStatus.DRAFT,
    val filedAtMillis: Long? = null,
    val filedWithRegulatorId: String? = null,                // FK -> Party.id (regulator)
    val regulatorCaseNumber: String? = null,                 // assigned after filing
    val amountClaimedMinorUnits: Long,
    val amountRecoveredMinorUnits: Long? = null,
    val recoveredAtMillis: Long? = null,
    val currency: String,                                    // matches the FeePayments
    val coverLetterText: String,                             // draft from Gemma; editable
    val draftDeliveryMessage: String,                        // for share-intent body
    val workerNotes: String? = null,
    val relatedComplaintPacketId: String? = null,            // optional: the wider complaint
)

enum class RefundStatus {
    DRAFT,            // worker started but hasn't filed
    FILED,            // submitted to regulator
    IN_REVIEW,        // regulator acknowledged, deliberating
    GRANTED,          // refund approved (full or partial)
    DENIED,           // claim rejected
    WITHDRAWN,        // worker pulled the claim
    PARTIALLY_RECOVERED, // some funds back, claim still open
}
