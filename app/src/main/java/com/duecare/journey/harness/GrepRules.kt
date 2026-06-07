package com.duecare.journey.harness

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Active GREP layer for the on-device advice prompt harness.
 *
 * Android v0.9.1 keeps the 9 most load-bearing worker-safety rules inline:
 * 4 core indicators + 5 adversarial-equivocation rules (the cross-domain
 * analogy-laundering move) synced from the parent harness `GREP_RULES`.
 * The wider deterministic report analyzer lives in `intel/DomainKnowledge.kt`
 * with 16 risk rules. The full desktop/web harness remains in the parent repo
 * and should be synced through build-time codegen in v1.
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
        // --- adversarial-equivocation rules (synced from parent GREP_RULES) ---
        // Detect the cross-domain analogy-laundering move: a benign-domain
        // analogy ("financial instrument", "like sponsoring an event") used to
        // normalize a labour-control arrangement. Each is allRequired = the
        // control term AND the analogy phrase, so benign uses don't fire.
        Rule(
            rule = "equivocation_bond_financial_instrument",
            patterns = listOf(
                Regex(
                    "\\b(?:security |cash |salary |completion )?bond\\b",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    "\\b(?:financial instrument|basically a (?:financial|deposit)|" +
                        "like a (?:deposit|bank) bond|just a deposit|" +
                        "standard (?:financial )?instrument)\\b",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "high",
            citation = "ILO C029 + P029 (2014); ILO C181 Art. 7 (no worker-paid " +
                "fees); ILO Forced Labour Indicator 4 (debt bondage)",
            indicator = "A worker-funded bond reframed as a benign financial " +
                "instrument is debt-bondage laundering via finance-domain " +
                "equivocation. The analogy does not change that the worker bears " +
                "a coercive, recruitment-related cost tied to completing the contract.",
        ),
        Rule(
            rule = "equivocation_broker_commission_normal",
            patterns = listOf(
                Regex("\\bbroker\\b", RegexOption.IGNORE_CASE),
                Regex(
                    "\\b(?:commission (?:is|are) normal|commission everywhere|" +
                        "brokers? (?:take|takes|earn)s? commission|" +
                        "standard business practice|normal business practice|" +
                        "like (?:a )?(?:stock|real ?estate|insurance) broker)\\b",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "high",
            citation = "ILO C181 Art. 7 (employer-pays principle); IRIS Standard",
            indicator = "A labour-broker fee charged to the worker, normalized by " +
                "analogy to finance or real-estate brokerage commission. The " +
                "employer-pays principle forbids charging the worker.",
        ),
        Rule(
            rule = "equivocation_sponsor_like_event",
            patterns = listOf(
                Regex("\\bsponsor(?:ship)?\\b", RegexOption.IGNORE_CASE),
                Regex(
                    "\\b(?:like sponsoring an event|just (?:the|a|my) sponsor|" +
                        "sponsorship is (?:just )?like|" +
                        "like (?:a )?(?:team|event|brand|title) sponsor|" +
                        "same as sponsoring)\\b",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "high",
            citation = "ILO C029 (restriction of movement); kafala reform standards " +
                "(exit-permit / NOC abolition)",
            indicator = "Kafala sponsor control over worker mobility reframed as " +
                "benign event or brand sponsorship. Tying a worker's ability to " +
                "change jobs or leave the country to a sponsor is a " +
                "restriction-of-movement indicator, not a marketing relationship.",
        ),
        Rule(
            rule = "equivocation_safekeeping_analogy",
            patterns = listOf(
                Regex(
                    "\\b(?:passport|identity document|travel document)s?\\b",
                    RegexOption.IGNORE_CASE,
                ),
                Regex("\\bsafe ?keeping\\b", RegexOption.IGNORE_CASE),
                Regex(
                    "\\b(?:cargo hold|vault|safe(?:ty)? deposit(?: box)?|locker|" +
                        "for protection|just storage|like (?:a )?bank)\\b",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "high",
            citation = "ILO C029 + P029; ILO Forced Labour Indicator 1 (retention " +
                "of identity documents)",
            indicator = "Passport or document confiscation laundered via a " +
                "safekeeping analogy (like a vault, cargo hold, or bank). The " +
                "worker is entitled to keep custody of identity documents at all times.",
        ),
        Rule(
            rule = "equivocation_charge_standard_business",
            patterns = listOf(
                Regex(
                    "\\b(?:charg\\w*|fees?|deduct\\w*)\\b",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    "\\b(?:worker|migrant|employee|applicant|recruit)s?\\b",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    "\\b(?:standard business practice|normal business practice|" +
                        "just business|every (?:industry|business|company) " +
                        "(?:does|charges)|cost of doing business)\\b",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            allRequired = true,
            severity = "high",
            citation = "ILO C181 Art. 7 (no direct or indirect worker-paid fees); " +
                "POEA / BP2MI worker-paid-zero rules",
            indicator = "A worker-charged recruitment fee normalized as standard " +
                "business practice. Recruitment costs fall on the employer under " +
                "the employer-pays principle, not as a normal cost passed to the worker.",
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
