package com.duecare.journey.journal

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A legal evaluation of an assessable entity (a FeePayment, a
 * Message, a Conversation, an Event). Always informational, never
 * gating: the worker can record actions the harness flagged as
 * ILLEGAL — that's the harm-reduction posture (paternalism resurfaces
 * if a worker can't record what actually happened to them).
 *
 * Source-of-truth audit trail: even if the worker overrides an
 * auto-assessment, the original `harnessVerdict` + `harnessReasoning`
 * fields preserve what the rules said. Future re-analysis can
 * compare the worker's claim against the harness's read.
 */
@Entity(tableName = "legal_assessments")
data class LegalAssessment(
    @PrimaryKey val id: String,                  // UUID
    val targetKind: AssessmentTarget,
    val targetId: String,                        // FK to FeePayment.id, Message.id, etc.
    val assessedAtMillis: Long,

    // What the harness deterministically computed (NEVER mutated
    // after creation — this is the audit trail).
    val harnessVerdict: AssessmentVerdict,
    val harnessReasoning: String,
    val controllingStatute: String? = null,      // e.g. "POEA MC 14-2017 §3"
    val controllingConvention: String? = null,   // e.g. "ILO C181 Art. 7"
    val grepHitsJoined: String = "",             // comma-separated rule names
    val ragDocsJoined: String = "",              // comma-separated doc IDs

    // What the worker chose to record. Defaults to == harness; can
    // be overridden in the UI without losing the harness data above.
    val workerVerdict: AssessmentVerdict = harnessVerdict,
    val workerNotes: String? = null,             // why the worker disagreed, if they did

    // Where the assessment originally came from.
    val source: AssessmentSource = AssessmentSource.HARNESS_AUTO,
)

enum class AssessmentTarget {
    FEE_PAYMENT,
    EVENT,
    MESSAGE,
    CONVERSATION,
    ATTACHMENT,
}

enum class AssessmentVerdict {
    LEGAL,
    ILLEGAL,
    GREY,           // some legal interpretations support, others don't
    UNVERIFIED,     // not enough info; needs more journal context
}

enum class AssessmentSource {
    HARNESS_AUTO,         // deterministic GREP rule fired
    GEMMA_INFERENCE,      // Gemma 4 read the entry + journal context and judged
    WORKER_CLAIMED,       // worker recorded their own claim without harness
    WORKER_OVERRIDE,      // worker disagreed with the auto-assessment
}
