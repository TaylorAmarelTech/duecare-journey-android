package com.duecare.journey.harness

import com.duecare.journey.intel.DomainKnowledge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessInfoTest {

    @Test
    fun harness_info_matches_domain_knowledge_counts() {
        assertEquals(
            DomainKnowledge.GrepRules.ALL.size,
            HarnessInfo.DOMAIN_RISK_RULE_COUNT,
        )
        assertEquals(
            DomainKnowledge.IloForcedLabourIndicators.ALL.size,
            HarnessInfo.ILO_INDICATOR_COUNT,
        )
        assertEquals(
            DomainKnowledge.CorridorKnowledge.ALL.size,
            HarnessInfo.CORRIDOR_PROFILE_COUNT,
        )
    }

    @Test
    fun harness_info_names_manifest_asset_and_repositories() {
        assertEquals("duecare_harness_manifest.json", HarnessInfo.MANIFEST_ASSET)
        assertTrue(HarnessInfo.ANDROID_REPO.contains("duecare-journey-android"))
        assertTrue(HarnessInfo.PARENT_REPO.contains("gemma4_comp"))
    }
}
