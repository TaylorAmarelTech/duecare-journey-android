package com.duecare.journey.intel

/**
 * Static domain knowledge ported from the gemma4_comp safety harness
 * (specifically `configs/duecare/domains/trafficking/`).
 *
 * Three layers:
 *
 *   1. [GrepRules]                — regex / keyword detectors that flag
 *                                   exploitation-shaped patterns in
 *                                   journal entries. Fast, deterministic,
 *                                   no model needed. Each rule maps to an
 *                                   ILO indicator + a statute citation +
 *                                   an NGO referral.
 *   2. [IloForcedLabourIndicators] — the eleven ILO C029 indicators of
 *                                    forced labour. The chat surface +
 *                                    NGO report lean on these as the
 *                                    canonical taxonomy.
 *   3. [CorridorKnowledge]         — for each migration corridor we know
 *                                    about: the legal placement-fee
 *                                    cap, the controlling regulator,
 *                                    and 1-3 NGO contacts. Drives the
 *                                    fee-illegality check + report
 *                                    generation.
 *
 * All content is public information sourced from official regulator
 * publications and public NGO directories. No PII. Safe to ship in the
 * APK.
 */
object DomainKnowledge {

    object GrepRules {

        /** Flag if matched anywhere in entry title+body. The
         *  [IloIndicator] reference grounds the flag in a recognized
         *  international taxonomy (so the chat surface can say "this
         *  matches ILO indicator 4: debt bondage" instead of just
         *  "this seems bad"). */
        data class Rule(
            val id: String,
            val displayName: String,
            val iloIndicator: Int,           // ILO C029 indicator number, 1-11
            val severity: Severity,
            val pattern: Regex,
            val statuteCitation: String,
            val whatItMeans: String,
            val nextStep: String,
        )

        enum class Severity { CRITICAL, HIGH, MEDIUM, LOW }

        val ALL: List<Rule> = listOf(
            Rule(
                id = "passport-withholding",
                displayName = "Passport / ID document withholding",
                iloIndicator = 8,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "(passport|visa|id|identification|document|paper)s?\\s+" +
                        "(was\\s+)?(taken|kept|held|withheld|confiscated|seized|" +
                        "for\\s+safekeeping|in\\s+the\\s+safe|with\\s+(?:the|my)\\s+" +
                        "(?:employer|recruiter|agency))",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 8 · UN Palermo Protocol Art. 3(a)",
                whatItMeans = "Withholding of identity documents is a primary " +
                    "indicator of forced labour. Even when framed as 'for " +
                    "safekeeping', it leaves the worker unable to leave the " +
                    "country, change employers, or seek help.",
                nextStep = "Photograph the document(s) before they are taken " +
                    "and store the photos in your journal. Contact your " +
                    "consulate immediately — they can intervene without " +
                    "your physical passport.",
            ),
            Rule(
                id = "wage-deduction-to-lender",
                displayName = "Salary deduction routed to a lender",
                iloIndicator = 4,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "(salary|wage|pay)(s|cheque)?\\s+(is\\s+)?" +
                        "(deduct|withhold|kept|sent|paid|routed|garnish)\\w*\\s+" +
                        "(to|by|for)\\s+(?:the\\s+)?(loan|lender|recruiter|agency|" +
                        "training|placement|debt)",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C095 Art. 9 · ILO C029 Indicator 4 (debt bondage)",
                whatItMeans = "Routing wages to a lender or recruiter rather " +
                    "than to the worker is a textbook debt-bondage pattern. " +
                    "ILO Convention 95 prohibits employers from making any " +
                    "deduction unless the worker has agreed in writing.",
                nextStep = "Demand to receive your full wages directly. Keep " +
                    "every payslip. If you have an unpaid loan, the loan must " +
                    "be against you personally, not a lien on your wages.",
            ),
            Rule(
                id = "extortionate-loan-rate",
                displayName = "Loan APR > 60% (HK extortionate threshold)",
                iloIndicator = 4,
                severity = Severity.HIGH,
                pattern = Regex(
                    "(\\d{2,3})\\s*%?\\s*(per\\s+annum|p\\.?a\\.?|apr|annual|" +
                        "yearly|/\\s*year)|(\\d{1,3})\\s*%\\s*(?:per\\s+)?month",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "Hong Kong Money Lenders Ord. Cap. 163 §24 " +
                    "(>60% APR is automatically extortionate)",
                whatItMeans = "Hong Kong law presumes any loan above 60% APR " +
                    "to be extortionate. The Philippines, Indonesia, Nepal, " +
                    "and Bangladesh have similar caps in the 36–48% range.",
                nextStep = "Capture the loan terms in writing. Free legal " +
                    "advice in HK: Duty Lawyer Service +852 2868 6101 · " +
                    "Mission for Migrant Workers +852 2522 8264.",
            ),
            Rule(
                id = "training-fee-camouflage",
                displayName = "Fee labelled 'training' / 'medical' / 'processing'",
                iloIndicator = 4,
                severity = Severity.HIGH,
                pattern = Regex(
                    "(training|medical|processing|admin|administrative|" +
                        "service|placement|orientation|seminar)\\s+fee",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "POEA Memo Circular 14-2017 · Indonesia " +
                    "Permenaker 9/2019 · Nepal FEA 2007 §22",
                whatItMeans = "Recruiters frequently relabel illegal " +
                    "placement fees as 'training', 'medical', or 'processing' " +
                    "fees. This is the most common fee-camouflage pattern in " +
                    "the Philippines→Hong Kong and Indonesia→Singapore corridors.",
                nextStep = "Note the label and the amount. The label doesn't " +
                    "change the legality — if your corridor caps placement " +
                    "fees, ANY fee above the cap is recoverable.",
            ),
            Rule(
                id = "movement-restriction",
                displayName = "Restricted movement / locked accommodation",
                iloIndicator = 5,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "(can(no|')t|cannot|not\\s+allowed|forbidden|prohibited|" +
                        "stopped|prevented)\\s+(to\\s+)?(leave|go\\s+out|exit|" +
                        "go\\s+outside|use\\s+phone)|" +
                        "(door|gate|room|house|accommodation|dorm)\\s+" +
                        "(is\\s+)?(locked|chained|barred)",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 5 (restriction of movement) · " +
                    "UDHR Art. 13",
                whatItMeans = "Restriction of movement — being prevented from " +
                    "leaving the workplace, accommodation, or country at " +
                    "will — is a primary forced-labour indicator.",
                nextStep = "If you can safely send a message: contact your " +
                    "consulate or the destination country's anti-trafficking " +
                    "hotline. In an emergency in HK: 999. In Singapore: 999. " +
                    "In Saudi Arabia: 911 / Tawasul +966 920003343.",
            ),
            Rule(
                id = "physical-violence-threat",
                displayName = "Physical violence / threat of violence",
                iloIndicator = 1,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "(hit|hits|hitting|slap|slapped|punch|punched|kick|kicked|" +
                        "beat|beaten|hurt|threaten|threatened|assault|attack)" +
                        "(ed|ing|s)?",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 1 (physical & sexual violence) · " +
                    "UN Palermo Protocol Art. 3(a)",
                whatItMeans = "Physical violence or credible threat of " +
                    "violence is the most severe ILO indicator and can " +
                    "trigger criminal charges against the abuser regardless " +
                    "of the worker's immigration status.",
                nextStep = "If safe: photograph injuries, save threatening " +
                    "messages. Call the destination-country police emergency " +
                    "number. Then contact your consulate.",
            ),
            Rule(
                id = "isolation",
                displayName = "Isolation from outside contact",
                iloIndicator = 6,
                severity = Severity.HIGH,
                pattern = Regex(
                    "(can(no|')t|cannot|not\\s+allowed|forbidden|stopped\\s+from)\\s+" +
                        "(call|calling|contact|contacting|talk|talking|message)\\s+" +
                        "(family|home|anyone|outside|friends?)",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 6 (isolation)",
                whatItMeans = "Restricting a worker's communication with " +
                    "family, friends, or the outside world is an ILO " +
                    "indicator and a common precursor to escalating abuse.",
                nextStep = "Memorize a hotline number. Try to keep a hidden " +
                    "or secondary phone. Anti-Slavery International: " +
                    "https://www.antislavery.org.",
            ),
            Rule(
                id = "wage-theft",
                displayName = "Unpaid or withheld wages",
                iloIndicator = 7,
                severity = Severity.HIGH,
                pattern = Regex(
                    "(not\\s+paid|unpaid|haven'?t\\s+been\\s+paid|hasn'?t\\s+paid|" +
                        "didn'?t\\s+pay|no\\s+(?:salary|wage|pay|payment))" +
                        "(\\s+for\\s+\\d+\\s+(?:day|week|month))?",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C095 (Protection of Wages) · ILO C029 Indicator 7",
                whatItMeans = "Withholding of wages — even temporarily, " +
                    "especially when used as leverage against a worker " +
                    "leaving — is an ILO forced-labour indicator.",
                nextStep = "Document every missed payday with date and " +
                    "amount expected. Most destination countries have a " +
                    "labour tribunal that hears wage claims regardless of " +
                    "immigration status.",
            ),
            Rule(
                id = "contract-substitution",
                displayName = "Contract substituted on arrival",
                iloIndicator = 9,
                severity = Severity.HIGH,
                pattern = Regex(
                    "(new|different|second|replaced|substitut|switch)\\w*\\s+contract|" +
                        "contract\\s+(is\\s+)?(different|changed|switched|replaced)",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 9 (deception) · " +
                    "UN Palermo Protocol Art. 3(a)",
                whatItMeans = "Replacing a worker's signed contract on " +
                    "arrival — typically with worse pay, longer hours, " +
                    "or a different employer — is a textbook deception " +
                    "indicator and a contract-substitution-trafficking pattern.",
                nextStep = "Keep a photo of BOTH contracts. The original " +
                    "is your primary evidence in any future tribunal claim.",
            ),
            Rule(
                id = "excessive-overtime",
                displayName = "Excessive overtime / no rest days",
                iloIndicator = 10,
                severity = Severity.MEDIUM,
                pattern = Regex(
                    "(\\d{2})\\s*(hour|hr)s?\\s+(per\\s+day|/day|a\\s+day)|" +
                        "no\\s+(rest|day\\s+off|break|holiday)|" +
                        "every\\s+day|24/7|seven\\s+days?\\s+a\\s+week",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 10 (abusive working conditions) · " +
                    "ILO C001 (Hours of Work) · destination-country labour codes",
                whatItMeans = "Working hours that consistently exceed the " +
                    "destination-country statutory limit (typically 48-60 " +
                    "hrs/week with at least one rest day) are an ILO " +
                    "indicator of abusive working conditions.",
                nextStep = "Record actual start and end times daily. " +
                    "Even informal time-tracking notes carry weight in a " +
                    "labour tribunal.",
            ),
            Rule(
                id = "intimidation-threat",
                displayName = "Threat of deportation / harm to family",
                iloIndicator = 2,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "(threat|threaten)\\w*\\s+(to\\s+)?" +
                        "(deport|cancel\\s+visa|send\\s+(you\\s+)?home|harm|" +
                        "tell\\s+(your\\s+)?family|hurt\\s+(your\\s+)?family|" +
                        "report\\s+to\\s+police|blacklist)",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C029 Indicator 2 (intimidation & threats)",
                whatItMeans = "Threats — of deportation, of harm to the " +
                    "worker's family back home, of false police reports — " +
                    "are coercion under both ILO and the UN Palermo " +
                    "Protocol, even without physical violence.",
                nextStep = "Save the threatening message verbatim. If " +
                    "verbal, write it down with date and witness if any. " +
                    "Threats are evidence.",
            ),
        )

        /** Run all rules over [text]. Returns matched rule IDs in
         *  severity order (CRITICAL first). Designed for a single
         *  journal entry; call once per add. */
        fun match(text: String): List<Rule> = ALL
            .filter { it.pattern.containsMatchIn(text) }
            .sortedBy { it.severity.ordinal }
    }

    /** ILO C029 forced-labour indicators (1-11). The harness uses these
     *  as a canonical taxonomy across all 21K test prompts. */
    object IloForcedLabourIndicators {
        data class Indicator(val number: Int, val name: String, val description: String)

        val ALL: List<Indicator> = listOf(
            Indicator(1, "Physical & sexual violence",
                "Beating, slapping, sexual assault, or any other corporal harm."),
            Indicator(2, "Intimidation & threats",
                "Threats of violence, deportation, denouncement to authorities, or harm to family members."),
            Indicator(3, "Restriction of movement",
                "Confinement at the workplace or accommodation; surveillance preventing free movement."),
            Indicator(4, "Debt bondage",
                "Inflated recruitment fees creating debt that the worker cannot reasonably repay; salary deductions to a lender."),
            Indicator(5, "Withholding of wages",
                "Wages held back as a deposit, withheld as discipline, or paid late as a control mechanism."),
            Indicator(6, "Isolation",
                "Confiscation of phones; restrictions on contacting family, friends, or compatriots."),
            Indicator(7, "Abuse of vulnerability",
                "Exploiting irregular immigration status, language barriers, or lack of local knowledge."),
            Indicator(8, "Retention of identity documents",
                "Confiscation of passport, visa, or other identity papers — preventing worker from leaving or seeking help."),
            Indicator(9, "Deception",
                "Misrepresentation of working conditions, wages, employer identity, or contract terms during recruitment."),
            Indicator(10, "Abusive working conditions",
                "Hours, safety, or conditions far below the destination-country legal minimum."),
            Indicator(11, "Excessive overtime",
                "Forced overtime beyond the legal limit; no rest days; punishment for refusing overtime."),
        )

        fun byNumber(n: Int): Indicator? = ALL.firstOrNull { it.number == n }
    }

    /** Per-corridor knowledge: the legal placement-fee cap, the
     *  controlling regulator, the destination-country labour tribunal,
     *  and trusted NGO contacts. */
    object CorridorKnowledge {

        data class Corridor(
            val code: String,                // e.g. "PH-HK"
            val originName: String,
            val destName: String,
            val placementFeeCapUsd: Double?, // null = no cap published
            val placementFeeNote: String,
            val originRegulator: Regulator,
            val destRegulator: Regulator,
            val ngoContacts: List<NgoContact>,
        )

        data class Regulator(
            val name: String,
            val url: String,
            val phone: String?,
        )

        data class NgoContact(
            val name: String,
            val service: String,
            val phone: String?,
            val url: String?,
        )

        val ALL: List<Corridor> = listOf(
            Corridor(
                code = "PH-HK",
                originName = "Philippines",
                destName = "Hong Kong",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Zero placement fee for Filipino domestic workers " +
                    "(POEA Memo Circular 14-2017). ANY fee paid is recoverable.",
                originRegulator = Regulator(
                    name = "DMW (formerly POEA) Anti-Illegal Recruitment Branch",
                    url = "https://dmw.gov.ph",
                    phone = "+63-2-8721-1144",
                ),
                destRegulator = Regulator(
                    name = "HK Labour Department - Foreign Domestic Helpers Section",
                    url = "https://www.labour.gov.hk/eng/public/wcp/FDHguide.pdf",
                    phone = "+852 2717 1771",
                ),
                ngoContacts = listOf(
                    NgoContact("Mission for Migrant Workers (Hong Kong)",
                        "Free legal advice + shelter referral",
                        "+852 2522 8264", "https://www.mfmw.org.hk"),
                    NgoContact("PathFinders HK", "Mothers + babies in distress",
                        "+852 5190 4886", "https://www.pathfinders.org.hk"),
                    NgoContact("HELP for Domestic Workers",
                        "Free legal clinic + counselling",
                        "+852 2523 4020", "https://helpfordomesticworkers.org"),
                ),
            ),
            Corridor(
                code = "ID-HK",
                originName = "Indonesia",
                destName = "Hong Kong",
                placementFeeCapUsd = 1850.0,
                placementFeeNote = "Indonesian Permenaker 9/2019 caps total " +
                    "placement-related fees at IDR 23.5M (~USD 1,850).",
                originRegulator = Regulator(
                    name = "BP2MI (Indonesian Migrant Worker Protection Agency)",
                    url = "https://bp2mi.go.id",
                    phone = "+62 21 3920 3411",
                ),
                destRegulator = Regulator(
                    name = "HK Labour Department - Foreign Domestic Helpers Section",
                    url = "https://www.labour.gov.hk/eng/public/wcp/FDHguide.pdf",
                    phone = "+852 2717 1771",
                ),
                ngoContacts = listOf(
                    NgoContact("Indonesian Migrant Workers Union (IMWU)",
                        "Peer support + tribunal accompaniment",
                        null, "https://www.imwu.hk"),
                    NgoContact("Mission for Migrant Workers (Hong Kong)",
                        "Free legal advice + shelter referral",
                        "+852 2522 8264", "https://www.mfmw.org.hk"),
                ),
            ),
            Corridor(
                code = "PH-SA",
                originName = "Philippines",
                destName = "Saudi Arabia",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Zero fee policy under the 2013 PH-KSA " +
                    "domestic-worker bilateral agreement.",
                originRegulator = Regulator(
                    name = "DMW (formerly POEA)",
                    url = "https://dmw.gov.ph",
                    phone = "+63-2-8721-1144",
                ),
                destRegulator = Regulator(
                    name = "Saudi Ministry of Human Resources (Musaned)",
                    url = "https://musaned.com.sa",
                    phone = "19911",
                ),
                ngoContacts = listOf(
                    NgoContact("Migrante International (PH-side)",
                        "Repatriation aid + legal referral",
                        null, "https://migranteinternational.org"),
                    NgoContact("Tawasul (Saudi worker hotline)",
                        "24/7 multi-language complaint line",
                        "+966 920003343", "https://musaned.com.sa"),
                ),
            ),
            Corridor(
                code = "NP-SA",
                originName = "Nepal",
                destName = "Saudi Arabia",
                placementFeeCapUsd = 100.0,
                placementFeeNote = "Nepal Free-Visa-Free-Ticket policy caps " +
                    "Saudi-bound migrant worker fees at NPR 10,000 (~USD 100).",
                originRegulator = Regulator(
                    name = "Department of Foreign Employment",
                    url = "https://dofe.gov.np",
                    phone = "+977 1 4258091",
                ),
                destRegulator = Regulator(
                    name = "Saudi Ministry of Human Resources",
                    url = "https://musaned.com.sa",
                    phone = "19911",
                ),
                ngoContacts = listOf(
                    NgoContact("People Forum for Human Rights (Nepal)",
                        "Legal aid + court representation",
                        "+977 1 4781309", "https://peopleforum.org.np"),
                    NgoContact("Pravasi Nepali Coordination Committee",
                        "Repatriation + grievance",
                        null, "https://pncc.org.np"),
                ),
            ),
            Corridor(
                code = "BD-SA",
                originName = "Bangladesh",
                destName = "Saudi Arabia",
                placementFeeCapUsd = 1850.0,
                placementFeeNote = "BMET caps Saudi-bound male worker fees " +
                    "at BDT 165,000 (~USD 1,850); women's domestic-worker " +
                    "fees are capped at zero.",
                originRegulator = Regulator(
                    name = "Bureau of Manpower, Employment & Training (BMET)",
                    url = "https://bmet.gov.bd",
                    phone = "+880 2 9357972",
                ),
                destRegulator = Regulator(
                    name = "Saudi Ministry of Human Resources",
                    url = "https://musaned.com.sa",
                    phone = "19911",
                ),
                ngoContacts = listOf(
                    NgoContact("BRAC Migration Programme",
                        "Pre-departure briefing + post-arrival aid",
                        null, "https://www.brac.net/migration"),
                    NgoContact("OKUP (Ovibashi Karmi Unnayan Program)",
                        "Returnee reintegration + legal aid",
                        null, "https://okup.org.bd"),
                ),
            ),
            Corridor(
                code = "ID-SG",
                originName = "Indonesia",
                destName = "Singapore",
                placementFeeCapUsd = 1100.0,
                placementFeeNote = "Indonesian regulations cap fees at " +
                    "approx. SGD 1,500 (~USD 1,100) for Singapore corridor; " +
                    "any salary-deduction loan-repayment scheme exceeding " +
                    "this is recoverable.",
                originRegulator = Regulator(
                    name = "BP2MI (Indonesian Migrant Worker Protection Agency)",
                    url = "https://bp2mi.go.id",
                    phone = "+62 21 3920 3411",
                ),
                destRegulator = Regulator(
                    name = "Singapore Ministry of Manpower (MOM)",
                    url = "https://www.mom.gov.sg",
                    phone = "+65 6438 5122",
                ),
                ngoContacts = listOf(
                    NgoContact("HOME (Humanitarian Org for Migration Economics)",
                        "24/7 helpline + shelter",
                        "+65 1800 797 7977", "https://www.home.org.sg"),
                    NgoContact("TWC2 (Transient Workers Count Too)",
                        "Workers' rights advice + meals programme",
                        "+65 6298 7831", "https://twc2.org.sg"),
                ),
            ),
        )

        fun byCode(code: String?): Corridor? =
            ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
