package com.duecare.journey.intel

import com.duecare.journey.journal.AssessmentSource
import com.duecare.journey.journal.AssessmentTarget
import com.duecare.journey.journal.AssessmentVerdict
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFeeAssessorTest {

    private val assessor = StructuredFeeAssessor()

    private fun fee(
        amountMinor: Long, currency: String = "PHP",
        purposeLabel: String = "training fee",
    ) = FeePayment(
        id = "fp1",
        paidAtMillis = 1_000L,
        amountMinorUnits = amountMinor,
        currency = currency,
        paidToId = "p1",
        purposeLabel = purposeLabel,
        purposeAsClaimedByPayer = "training",
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        stage = JourneyStage.PRE_DEPARTURE,
    )

    @Test
    fun zero_fee_corridor_flags_any_placement_shaped_payment() {
        val a = assessor.assess(fee(5_000_000L, "PHP", "training fee"), "PH-HK")
        assertNotNull(a)
        assertEquals(AssessmentVerdict.ILLEGAL, a!!.harnessVerdict)
        assertEquals(AssessmentTarget.FEE_PAYMENT, a.targetKind)
        assertEquals("fp1", a.targetId)
        assertEquals(AssessmentSource.HARNESS_AUTO, a.source)
    }

    @Test
    fun zero_fee_corridor_does_not_flag_non_placement_shaped_payment() {
        val a = assessor.assess(fee(5_000_000L, "PHP", "lunch reimbursement"), "PH-HK")
        assertNull("Lunch reimbursement should not be flagged", a)
    }

    @Test
    fun nepal_corridor_flags_excess_above_cap() {
        // Cap is USD 100. NPR 1,000,000 = ~USD 7,500 — way over.
        val a = assessor.assess(fee(100_000_000L, "NPR", "service fee"), "NP-SA")
        assertNotNull(a)
        assertTrue(a!!.harnessReasoning.contains("NP-SA"))
    }

    @Test
    fun no_corridor_means_no_assessment() {
        val a = assessor.assess(fee(5_000_000L, "PHP"), null)
        assertNull(a)
    }

    @Test
    fun unknown_corridor_means_no_assessment() {
        val a = assessor.assess(fee(5_000_000L, "PHP"), "XX-YY")
        assertNull(a)
    }

    @Test
    fun assessment_includes_controlling_statute_for_PH_HK() {
        val a = assessor.assess(fee(5_000_000L, "PHP"), "PH-HK")
        assertNotNull(a)
        assertTrue(
            "Expected POEA MC reference",
            a!!.controllingStatute?.contains("POEA") == true,
        )
        assertTrue(
            "Expected ILO C181 reference",
            a.controllingConvention?.contains("C181") == true,
        )
    }

    @Test
    fun grep_hits_recorded_for_audit_trail() {
        val a = assessor.assess(fee(5_000_000L, "PHP"), "PH-HK")
        assertNotNull(a)
        assertEquals("fee_corridor_cap_violation", a!!.grepHitsJoined)
    }
}
