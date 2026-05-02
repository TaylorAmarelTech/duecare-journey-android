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
}
