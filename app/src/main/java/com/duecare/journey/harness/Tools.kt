package com.duecare.journey.harness

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin port of the 4 lookup functions from the Python harness Tools
 * layer: corridor fee caps, fee camouflage, ILO indicator matcher,
 * NGO intake hotlines.
 *
 * STATUS (skeleton): only `lookup_corridor_fee_cap` and
 * `lookup_ngo_intake` are inline as proof-of-concept. The full 4-tool
 * port lands in v1 MVP (along with the structured tool-call dispatch
 * from Gemma's native function-calling API once LiteRT supports it).
 */
@Singleton
class Tools @Inject constructor() {

    fun lookup(question: String, corridor: String?): List<String> {
        val out = mutableListOf<String>()
        corridor?.let { c ->
            corridorFeeCap(c)?.let { out += it }
            ngoIntake(c)?.let { out += it }
        }
        return out
    }

    private fun corridorFeeCap(corridor: String): String? = when (corridor) {
        "PH-HK" -> "POEA MC 14-2017: PH→HK domestic worker fee cap = 0 PHP " +
            "(zero placement fee, employer pays all)."
        "PH-SA" -> "POEA: PH→SA recruitment fee subject to standard caps; " +
            "verify corridor-specific MC."
        "ID-HK" -> "BP2MI Reg. 9/2020: PMI cost components per Article 36 — " +
            "medical, training, documentation, visa, airfare, insurance " +
            "EXCLUDED from worker burden."
        "NP-QA" -> "Nepal FEA 2007 §11(2): max NPR 10,000 service fee. " +
            "Free Visa Free Ticket policy: employer covers visa + airfare."
        "BD-SA" -> "Bangladesh OEA 2013 §17: max BDT 4,000 service fee " +
            "(unskilled) / BDT 6,000-15,000 (skilled). Anything above is " +
            "presumptively illegal recruitment."
        else -> null
    }

    private fun ngoIntake(corridor: String): String? = when (corridor) {
        "PH-HK" -> "Hotlines: POEA Anti-Illegal Recruitment Branch " +
            "+63-2-8721-1144; Mission for Migrant Workers HK " +
            "+852-2522-8264; PHL Consulate HK +852-2823-8500."
        "ID-HK" -> "Hotlines: BP2MI +62-21-2924-4800; Indonesian Migrant " +
            "Workers Union HK +852-2723-7536; Indonesian Consulate HK " +
            "+852-3651-0200."
        "NP-QA" -> "Hotlines: Nepal DoFE +977-1-4525-321; Pravasi Nepali " +
            "Coordination Committee Qatar; Nepal Embassy Doha."
        "BD-SA" -> "Hotlines: BMET +880-2-9357972; WARBE; Bangladesh " +
            "Embassy Riyadh."
        else -> null
    }
}
