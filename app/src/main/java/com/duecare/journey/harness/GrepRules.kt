package com.duecare.journey.harness

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin port of the 37-rule GREP catalog from
 * `packages/duecare-llm-chat/src/duecare/chat/harness/__init__.py`.
 *
 * STATUS (skeleton): only the 4 most load-bearing rules are inline
 * here as proof-of-concept. The full 37-rule port lands in v1 MVP
 * via a build-time codegen step (Python rules JSON -> Kotlin object).
 * This keeps the rule-set source-of-truth in one place across all
 * deployment surfaces.
 */
@Singleton
class GrepRules @Inject constructor() {

    private data class Rule(
        val rule: String,
        val patterns: List<Regex>,
        val allRequired: Boolean,
        val severity: String,
        val citation: String,
        val indicator: String,
    )

    private val rules: List<Rule> = listOf(
        Rule(
            rule = "usury_pattern_high_apr",
            patterns = listOf(
                Regex(
                    "\\b(\\d{2,3})\\s*%\\s*(?:per|/)?\\s*" +
                        "(?:year|annum|annual|yearly|p\\.a\\.)\\b",
                    RegexOption.IGNORE_CASE,
                )
            ),
            allRequired = false,
            severity = "high",
            citation = "ILO C029 §2; HK Money Lenders Ord. Cap. 163 §24 " +
                "(criminalises >60% APR); Indonesia OJK Reg. 10/POJK.05/2022",
            indicator = "Predatory APR. ILO forced-labor indicator 2 " +
                "(debt bondage). Worker consent does NOT neutralize this risk.",
        ),
        Rule(
            rule = "debt_bondage_loan_salary_deduction",
            patterns = listOf(
                Regex("\\bloan(s|ed|ing)?\\b", RegexOption.IGNORE_CASE),
                Regex(
                    "\\b(salary|wage|payroll)\\b.{0,80}" +
                        "\\b(deduct|deduction|withhold|garnish)",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "critical",
            citation = "ILO C029 §1; ILO C095 Art. 8; HK Cap. 57 §32; " +
                "PH RA 8042 / RA 10022 §7",
            indicator = "Worker-funded loan + salary deduction is the " +
                "textbook ILO debt-bondage pattern.",
        ),
        Rule(
            rule = "passport_safekeeping_consent_form",
            patterns = listOf(
                Regex(
                    "\\bpassport\\b.*\\b(retained|held|kept)\\s+(by|with)\\s+" +
                        "(the\\s+)?(sponsor|employer|household|family)\\b.*\\b" +
                        "(safekeeping|safe[- ]keeping|protect|safety)\\b",
                    RegexOption.IGNORE_CASE,
                )
            ),
            allRequired = false,
            severity = "critical",
            citation = "ILO C189 Art. 9; Saudi MoHR Domestic Worker " +
                "Regulation Art. 6; ILO Forced Labour Indicator #8",
            indicator = "Passport retention by employer is ILO Indicator 8 " +
                "(Withholding of documents). Worker consent is irrelevant " +
                "per Palermo Protocol Art. 3(b).",
        ),
        Rule(
            rule = "huroob_threat",
            patterns = listOf(
                Regex("\\bhuroob\\b", RegexOption.IGNORE_CASE),
                Regex("\\babsconder\\b", RegexOption.IGNORE_CASE),
            ),
            allRequired = false,
            severity = "critical",
            citation = "Saudi MoHR Labour Law (kafala reforms 2021/2024); " +
                "ILO C029; Palermo Protocol; ICRMW Art. 22",
            indicator = "'Huroob' (absconder) status is a documented " +
                "coercion pattern. Recent Saudi reforms reduce but don't " +
                "eliminate. Advise the worker to contact embassy attaché.",
        ),
    )

    /** Run all rules against [text]; return matched rules. */
    fun match(text: String): List<GrepHit> {
        val out = mutableListOf<GrepHit>()
        if (text.isBlank()) return out
        for (r in rules) {
            val matches = r.patterns.map { p ->
                p.find(text)
            }
            if (r.allRequired && matches.any { it == null }) continue
            if (!r.allRequired && matches.all { it == null }) continue
            val firstMatch = matches.firstOrNull { it != null }
            val excerpt = firstMatch?.let { m ->
                val s = (m.range.first - 30).coerceAtLeast(0)
                val e = (m.range.last + 30).coerceAtMost(text.length - 1)
                text.substring(s, e + 1).replace("\n", " ")
            }.orEmpty()
            out.add(
                GrepHit(
                    rule = r.rule,
                    severity = r.severity,
                    citation = r.citation,
                    indicator = r.indicator,
                    matchExcerpt = "…$excerpt…",
                )
            )
        }
        return out
    }
}
