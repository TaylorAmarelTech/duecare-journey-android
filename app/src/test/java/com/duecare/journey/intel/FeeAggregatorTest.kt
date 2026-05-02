package com.duecare.journey.intel

import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.Party
import com.duecare.journey.journal.PartyKind
import com.duecare.journey.journal.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeAggregatorTest {

    private val agg = FeeAggregator()

    private val phAgency = Party(
        id = "p1",
        kind = PartyKind.RECRUITMENT_AGENCY,
        name = "Pacific Coast Manpower Inc.",
        country = "PH",
    )

    private fun structuredFee(
        amountMinor: Long,
        currency: String,
        purposeLabel: String = "training fee",
    ) = FeePayment(
        id = "fp1",
        paidAtMillis = 1_000L,
        amountMinorUnits = amountMinor,
        currency = currency,
        paidToId = phAgency.id,
        purposeLabel = purposeLabel,
        purposeAsClaimedByPayer = "mandatory training",
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        stage = JourneyStage.PRE_DEPARTURE,
    )

    @Test
    fun build_aggregates_structured_fee_and_resolves_party_name() {
        val report = agg.build(
            structured = listOf(structuredFee(5_000_000L, "PHP")),
            parties = listOf(phAgency),
            journalEntries = emptyList(),
            corridorCode = "PH-HK",
        )
        assertEquals(1, report.lines.size)
        assertEquals("Pacific Coast Manpower Inc.", report.lines.first().recipientName)
        assertEquals("PHP", report.lines.first().currency)
    }

    @Test
    fun build_flags_zero_fee_corridor_violation_as_illegal() {
        val report = agg.build(
            structured = listOf(structuredFee(5_000_000L, "PHP")),
            parties = listOf(phAgency),
            journalEntries = emptyList(),
            corridorCode = "PH-HK",
        )
        val line = report.lines.first()
        assertTrue("Expected line to be flagged illegal", line.isProbablyIllegal)
        assertNotNull(line.illegalityReason)
    }

    @Test
    fun build_does_not_flag_when_corridor_unset() {
        val report = agg.build(
            structured = listOf(structuredFee(5_000_000L, "PHP")),
            parties = listOf(phAgency),
            journalEntries = emptyList(),
            corridorCode = null,
        )
        assertTrue(report.illegalLines.isEmpty())
    }

    @Test
    fun build_extracts_fee_from_journal_entry_text() {
        val journalEntry = JournalEntry(
            id = "e1",
            timestampMillis = 1L,
            stage = JourneyStage.PRE_DEPARTURE,
            kind = EntryKind.EXPENSE,
            title = "Paid training fee",
            body = "Sent ₱50,000 to the agency for the training fee",
            parties = listOf("Pacific Coast Manpower"),
        )
        val report = agg.build(
            structured = emptyList(),
            parties = emptyList(),
            journalEntries = listOf(journalEntry),
            corridorCode = "PH-HK",
        )
        assertTrue("Expected at least one extracted line", report.lines.isNotEmpty())
        assertEquals("PHP", report.lines.first().currency)
    }

    @Test
    fun structured_lines_carry_feePaymentId_extracted_lines_do_not() {
        val journalEntry = JournalEntry(
            id = "e1", timestampMillis = 1L,
            stage = JourneyStage.PRE_DEPARTURE, kind = EntryKind.EXPENSE,
            title = "x", body = "Paid ₱50,000",
        )
        val report = agg.build(
            structured = listOf(structuredFee(5_000_000L, "PHP")),
            parties = listOf(phAgency),
            journalEntries = listOf(journalEntry),
            corridorCode = "PH-HK",
        )
        val structured = report.lines.first { it.sourceKind == FeeAggregator.SourceKind.STRUCTURED }
        val extracted = report.lines.first { it.sourceKind == FeeAggregator.SourceKind.EXTRACTED_FROM_JOURNAL }
        assertEquals("fp1", structured.feePaymentId)
        assertEquals(null, extracted.feePaymentId)
    }

    @Test
    fun totals_by_currency_sums_correctly() {
        val report = agg.build(
            structured = listOf(
                structuredFee(5_000_000L, "PHP"),
                structuredFee(1_000_000L, "PHP"),
            ),
            parties = listOf(phAgency),
            journalEntries = emptyList(),
            corridorCode = "PH-HK",
        )
        val phpTotal = report.totalsByCurrency.first { it.currency == "PHP" }
        assertEquals(6_000_000L, phpTotal.totalMinorUnits)
    }
}
