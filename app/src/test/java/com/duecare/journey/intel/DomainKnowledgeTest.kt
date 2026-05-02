package com.duecare.journey.intel

import com.duecare.journey.intel.DomainKnowledge.CorridorKnowledge
import com.duecare.journey.intel.DomainKnowledge.GrepRules
import com.duecare.journey.intel.DomainKnowledge.IloForcedLabourIndicators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the static domain-knowledge layer. These don't need
 * any Android runtime — pure JVM JUnit. They prove that:
 *
 *   1. GREP rules fire on canonical exploitation language.
 *   2. ILO indicator coverage is canonical (1-11, no gaps).
 *   3. Each corridor profile has a regulator + at least one NGO.
 */
class DomainKnowledgeTest {

    @Test
    fun grep_rules_fire_on_canonical_passport_withholding_phrasing() {
        val text = "My recruiter is keeping my passport for safekeeping until I leave."
        val matches = GrepRules.match(text)
        assertTrue(
            "Expected passport-withholding rule to fire on $text",
            matches.any { it.id == "passport-withholding" }
        )
    }

    @Test
    fun grep_rules_fire_on_extortionate_apr() {
        val text = "The agency wants 68% per annum on the placement loan."
        val matches = GrepRules.match(text)
        assertTrue(
            "Expected extortionate-loan-rate rule to fire on $text",
            matches.any { it.id == "extortionate-loan-rate" }
        )
    }

    @Test
    fun grep_rules_fire_on_training_fee_camouflage() {
        val text = "Paid PHP 50,000 training fee before they would book my flight"
        val matches = GrepRules.match(text)
        assertTrue(
            "Expected training-fee-camouflage rule to fire on $text",
            matches.any { it.id == "training-fee-camouflage" }
        )
    }

    @Test
    fun grep_rules_fire_on_movement_restriction() {
        val text = "I am not allowed to leave the house, the door is locked at night"
        val matches = GrepRules.match(text)
        assertTrue(
            "Expected movement-restriction rule to fire on $text",
            matches.any { it.id == "movement-restriction" }
        )
    }

    @Test
    fun grep_rules_fire_on_wage_withholding() {
        val text = "I have not been paid for 3 months. The employer keeps saying next month."
        val matches = GrepRules.match(text)
        assertTrue(
            "Expected wage-theft rule to fire on $text",
            matches.any { it.id == "wage-theft" }
        )
    }

    @Test
    fun grep_rules_do_not_fire_on_benign_text() {
        val text = "Today I went to the market with my friend and bought groceries."
        val matches = GrepRules.match(text)
        assertEquals(
            "Expected zero matches on $text but got $matches",
            0, matches.size,
        )
    }

    @Test
    fun grep_rules_sort_critical_before_high() {
        val text = "Recruiter is keeping my passport AND charging a 50,000 training fee."
        val matches = GrepRules.match(text)
        assertTrue("Expected at least 2 matches", matches.size >= 2)
        // First match should be CRITICAL severity (passport withholding)
        assertEquals(GrepRules.Severity.CRITICAL, matches.first().severity)
    }

    @Test
    fun ilo_indicators_cover_one_through_eleven() {
        val numbers = IloForcedLabourIndicators.ALL.map { it.number }.toSet()
        assertEquals((1..11).toSet(), numbers)
    }

    @Test
    fun ilo_indicators_have_nonblank_names_and_descriptions() {
        IloForcedLabourIndicators.ALL.forEach {
            assertTrue("Indicator ${it.number} has blank name", it.name.isNotBlank())
            assertTrue("Indicator ${it.number} has blank description", it.description.isNotBlank())
        }
    }

    @Test
    fun every_corridor_has_regulator_plus_at_least_one_ngo() {
        CorridorKnowledge.ALL.forEach { corridor ->
            assertNotNull("Corridor ${corridor.code} missing origin regulator", corridor.originRegulator)
            assertNotNull("Corridor ${corridor.code} missing dest regulator", corridor.destRegulator)
            assertTrue(
                "Corridor ${corridor.code} has no NGO contacts",
                corridor.ngoContacts.isNotEmpty(),
            )
        }
    }

    @Test
    fun corridor_lookup_is_case_insensitive() {
        val a = CorridorKnowledge.byCode("PH-HK")
        val b = CorridorKnowledge.byCode("ph-hk")
        assertNotNull(a)
        assertEquals(a, b)
    }

    @Test
    fun zero_fee_corridors_are_marked_as_such() {
        val phHk = CorridorKnowledge.byCode("PH-HK")
        assertNotNull(phHk)
        assertEquals(0.0, phHk!!.placementFeeCapUsd!!, 0.001)
    }

    @Test
    fun all_twenty_corridors_present() {
        val codes = CorridorKnowledge.ALL.map { it.code }.toSet()
        val expected = setOf(
            // Asia → Gulf / HK / SG (the original 6)
            "PH-HK", "ID-HK", "PH-SA", "NP-SA", "BD-SA", "ID-SG",
            // Latin America
            "MX-US", "VE-CO",
            // West Africa → Lebanon (kafala)
            "GH-LB", "NG-LB",
            // Refugee corridors
            "SY-DE", "UA-PL",
            // v0.9 expansion: Asia → Europe / Asia → Asia
            "PH-IT", "ID-TW", "MM-TH", "KH-MY",
            // East Africa → Gulf
            "ET-LB", "KE-SA",
            // Refugee + intra-Africa
            "AF-IR", "ZW-ZA",
        )
        assertEquals(expected, codes)
    }

    @Test
    fun mexico_us_h2_corridor_is_zero_fee() {
        val mxUs = CorridorKnowledge.byCode("MX-US")
        assertNotNull(mxUs)
        assertEquals(0.0, mxUs!!.placementFeeCapUsd!!, 0.001)
        // Statute citation should reference H-2 / DOL
        assertTrue(
            "PH note should mention H-2 visa programs",
            mxUs.placementFeeNote.contains("H-2", ignoreCase = true) ||
                mxUs.placementFeeNote.contains("DOL", ignoreCase = true),
        )
    }

    @Test
    fun ukraine_poland_temp_protection_is_zero_fee() {
        val uaPl = CorridorKnowledge.byCode("UA-PL")
        assertNotNull(uaPl)
        assertEquals(0.0, uaPl!!.placementFeeCapUsd!!, 0.001)
    }

    @Test
    fun refugee_corridors_have_destination_regulator_even_if_origin_is_null() {
        // Syria post-2011: no functional origin regulator
        val syDe = CorridorKnowledge.byCode("SY-DE")
        assertNotNull(syDe)
        assertNotNull("Even refugee corridors must name a destination regulator",
            syDe!!.destRegulator)
        assertNotNull("BAMF should be the German destination regulator",
            syDe.destRegulator.name.contains("BAMF") ||
                syDe.destRegulator.name.contains("Migration"))
    }

    @Test
    fun new_corridors_each_have_at_least_two_ngo_contacts() {
        val newCorridors = listOf(
            // v0.8 wave
            "MX-US", "VE-CO", "GH-LB", "NG-LB", "SY-DE", "UA-PL",
            // v0.9 wave
            "PH-IT", "ID-TW", "MM-TH", "KH-MY", "ET-LB", "KE-SA",
            "AF-IR", "ZW-ZA",
        )
        newCorridors.forEach { code ->
            val c = CorridorKnowledge.byCode(code)
            assertNotNull("Missing corridor: $code", c)
            assertTrue(
                "Corridor $code should have ≥ 2 NGO contacts",
                c!!.ngoContacts.size >= 2,
            )
        }
    }

    @Test
    fun ph_it_zero_fee_decreto_flussi() {
        val c = CorridorKnowledge.byCode("PH-IT")
        assertNotNull(c)
        assertEquals(0.0, c!!.placementFeeCapUsd!!, 0.001)
        assertTrue(c.placementFeeNote.contains("Decreto Flussi", ignoreCase = true) ||
                   c.placementFeeNote.contains("bilateral", ignoreCase = true))
    }

    @Test
    fun mm_th_corridor_notes_fishing_industry_risk() {
        val c = CorridorKnowledge.byCode("MM-TH")
        assertNotNull(c)
        assertTrue(c!!.placementFeeNote.contains("fishing", ignoreCase = true))
    }

    @Test
    fun et_lb_corridor_kafala_pattern() {
        val c = CorridorKnowledge.byCode("ET-LB")
        assertNotNull(c)
        assertTrue(c!!.placementFeeNote.contains("kafala", ignoreCase = true))
    }

    // ─── New GREP rules (corridor-specific patterns) ──────────

    @Test
    fun grep_rule_kafala_huroob_fires_on_canonical_text() {
        val matches = GrepRules.match("My kafeel filed huroob against me last week")
        assertTrue(matches.any { it.id == "kafala-huroob-absconder" })
    }

    @Test
    fun grep_rule_h2_visa_fee_violation_fires() {
        val text = "On my H-2A visa, the contractor charged me \$500 in transportation deductions"
        val matches = GrepRules.match(text)
        assertTrue(matches.any { it.id == "h2a-h2b-fee-violation" })
    }

    @Test
    fun grep_rule_fishing_vessel_pattern_fires() {
        val text = "I was on a fishing vessel for 18 months. The captain held my " +
                   "passport and deducted my recruitment loan from my pay."
        val matches = GrepRules.match(text)
        assertTrue("Expected fishing-vessel-debt-confinement to fire on $text",
            matches.any { it.id == "fishing-vessel-debt-confinement" })
    }

    @Test
    fun grep_rule_smuggler_fee_pattern_fires() {
        val text = "We paid the smuggler USD 1500 to take us across the border"
        val matches = GrepRules.match(text)
        assertTrue(matches.any { it.id == "smuggler-fee-and-coercion" })
    }

    @Test
    fun grep_rule_domestic_live_in_pattern_fires() {
        val text = "My employer requires me to live-in and be available 24/7 with no rest day"
        val matches = GrepRules.match(text)
        assertTrue(matches.any { it.id == "domestic-work-locked-in-residence" })
    }
}
