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
            // ── Corridor-specific patterns (v0.9 expansion) ───────
            Rule(
                id = "kafala-huroob-absconder",
                displayName = "Kafala 'huroob' / 'absconder' status",
                iloIndicator = 5,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "\\b(huroob|absconder|absconding|tasreeh|exit\\s+visa\\s+denied|" +
                        "kafeel\\s+refus\\w+|sponsor\\s+refus\\w+\\s+(to\\s+)?(release|let\\s+me\\s+go))\\b",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "Saudi MoHR Domestic Worker Regulation Art. 6 " +
                    "(reformed 2021/2024); Lebanese General Security " +
                    "kafala framework; ILO C029 Indicator 5",
                whatItMeans = "'Huroob' (Saudi) / 'absconder' is a " +
                    "kafala-system status the kafeel/sponsor files when " +
                    "they want to deny a worker the right to change " +
                    "employer or leave the country. Recent Saudi reforms " +
                    "reduce but don't eliminate the abuse vector — and " +
                    "Lebanon hasn't reformed at all.",
                nextStep = "Contact the embassy attaché immediately. " +
                    "Kafala-status disputes are time-sensitive — workers " +
                    "with huroob status can be detained without notice.",
            ),
            Rule(
                id = "h2a-h2b-fee-violation",
                displayName = "H-2A / H-2B recruitment-fee violation",
                iloIndicator = 4,
                severity = Severity.HIGH,
                pattern = Regex(
                    "\\b(h-?2[ab]|temporary\\s+(agricultural|non-agricultural)\\s+work|" +
                        "petition|labor\\s+certification)\\b.*\\b" +
                        "(fee|charge|cost|paid|charged|deduct\\w*)\\b|" +
                        "\\b(transportation\\s+deduct|housing\\s+deduct|" +
                        "tool\\s+rental|equipment\\s+rental)\\b",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "US DOL 20 CFR 655.135(j) (H-2A); " +
                    "20 CFR 655.20(p) (H-2B); INA Sec. 274C",
                whatItMeans = "US H-2A and H-2B visa programs prohibit " +
                    "the worker from bearing recruitment costs (visa " +
                    "fees, transportation, housing). Charges flow to the " +
                    "US employer or to the recruiter; the worker's pay " +
                    "must not be reduced below the AEWR (Adverse Effect " +
                    "Wage Rate) by recruitment-related deductions.",
                nextStep = "Document the deduction (date, amount, label " +
                    "the employer used). File with US DOL Wage and Hour " +
                    "Division: 1-866-487-9243 or wagehour.dol.gov.",
            ),
            Rule(
                id = "fishing-vessel-debt-confinement",
                displayName = "Fishing-vessel debt + confinement-at-sea pattern",
                iloIndicator = 4,
                severity = Severity.CRITICAL,
                pattern = Regex(
                    "\\b(fishing\\s+(boat|vessel|trawler)|sea\\s+work|" +
                        "(at|on)\\s+sea\\s+for\\s+\\d+\\s+(month|year)|" +
                        "transhipment|reefer|long-?liner|purse\\s+seiner)\\b.*\\b" +
                        "(debt|loan|advance|deduct|withhold|" +
                        "(can(no|')t|cannot|not\\s+allowed)\\s+to\\s+(leave|go\\s+ashore))\\b",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C188 (Work in Fishing Convention); " +
                    "ILO C029 Indicators 4 (debt bondage) + 5 (movement " +
                    "restriction) + 8 (passport withholding); Thailand " +
                    "Royal Ordinance Concerning Sea Fishery 2015",
                whatItMeans = "The combination of recruitment debt + " +
                    "extended at-sea confinement + transhipment-at-sea " +
                    "(workers transferred between vessels without " +
                    "touching shore) is the documented forced-labour " +
                    "pattern in Thai/Indonesian/Taiwanese fishing fleets. " +
                    "Workers may go 1-3 years without setting foot on land.",
                nextStep = "If the worker is currently at sea: report to " +
                    "Issara Institute hotline (Thailand: +66 2 245 2380) " +
                    "or Stella Maris (international port chaplaincy network). " +
                    "If returned: document with ILO ship-by-ship reporting.",
            ),
            Rule(
                id = "smuggler-fee-and-coercion",
                displayName = "Smuggler-fee + coercion pattern (refugee corridor)",
                iloIndicator = 9,
                severity = Severity.HIGH,
                pattern = Regex(
                    "\\b(smuggler|trafficker|coyote|guia|" +
                        "(USD|EUR|\\$|€)\\s*\\d{3,4}\\s+(to\\s+cross|to\\s+take\\s+me|" +
                        "to\\s+get\\s+(across|to)))\\b|" +
                        "\\b(crossing\\s+fee|passage\\s+fee)\\b",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "UN Palermo Protocol Art. 3(a); UN " +
                    "Smuggling-of-Migrants Protocol Art. 3; ILO C029 " +
                    "Indicator 9 (deception)",
                whatItMeans = "Smuggling fees are not themselves illegal " +
                    "for the migrant — but the deception/coercion pattern " +
                    "around them (false promises about destination, " +
                    "extortion of additional fees mid-journey, sale of " +
                    "the migrant to a third party at destination) is " +
                    "trafficking under Palermo Art. 3(a).",
                nextStep = "If en route: the destination country's " +
                    "anti-trafficking hotline often has multilingual " +
                    "support and can intervene without exposing the " +
                    "smuggler relationship to immigration authorities. " +
                    "If arrived: a refugee-protection NGO can document " +
                    "the deception pattern + advocate for trafficking-" +
                    "victim status (which carries different protections " +
                    "than asylum).",
            ),
            Rule(
                id = "domestic-work-locked-in-residence",
                displayName = "Domestic worker required to live-in + 24/7 availability",
                iloIndicator = 5,
                severity = Severity.HIGH,
                pattern = Regex(
                    "\\b(live-?in|sleep\\s+at\\s+(employer|household)|" +
                        "must\\s+stay\\s+in\\s+(the\\s+)?house|" +
                        "(no|cannot|can(no|')t)\\s+(go|leave)\\s+(home|out)|" +
                        "(available|on\\s+call)\\s+(24/7|all\\s+night|whenever))\\b",
                    RegexOption.IGNORE_CASE,
                ),
                statuteCitation = "ILO C189 Arts. 9 + 10 (Domestic Workers " +
                    "Convention — entitled to keep travel docs + free to " +
                    "reach an agreement on whether to reside in the " +
                    "household); ILO C029 Indicators 5 + 11",
                whatItMeans = "ILO C189 specifically protects domestic " +
                    "workers' right to choose whether to live in the " +
                    "employer's household and to retain their identity " +
                    "documents. Forced live-in arrangements are a primary " +
                    "kafala-system + HK FDH abuse pattern.",
                nextStep = "Document the working hours pattern (when " +
                    "shifts start/end + when sleep happens + breaks). " +
                    "ILO C189 + most destination-country labour codes " +
                    "require either separate accommodation OR genuinely " +
                    "off-duty hours within the household.",
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
            // ── Latin America ────────────────────────────────────
            Corridor(
                code = "MX-US",
                originName = "Mexico",
                destName = "United States",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Both H-2A (agricultural) and H-2B " +
                    "(non-agricultural) US visa programs prohibit the " +
                    "worker from paying recruitment fees per US DOL " +
                    "regulation 20 CFR 655.135(j) and the H-2 " +
                    "Migrant-Labor Protections. Recoverable from US " +
                    "employer + Mexican recruiter jointly.",
                originRegulator = Regulator(
                    name = "Secretaría del Trabajo y Previsión Social (STPS)",
                    url = "https://www.gob.mx/stps",
                    phone = "+52 55 2000 5300",
                ),
                destRegulator = Regulator(
                    name = "US Dept of Labor — Wage & Hour Division",
                    url = "https://www.dol.gov/agencies/whd",
                    phone = "+1 866 487 9243",
                ),
                ngoContacts = listOf(
                    NgoContact("Centro de los Derechos del Migrante (CDM)",
                        "Cross-border legal aid + Sue Tu Empleador hotline",
                        "+1 855 234 9699", "https://cdmigrante.org"),
                    NgoContact("Polaris Project",
                        "US national trafficking hotline (multi-language)",
                        "+1 888 373 7888", "https://polarisproject.org"),
                    NgoContact("Farmworker Justice",
                        "H-2A worker advocacy",
                        "+1 202 293 5420", "https://www.farmworkerjustice.org"),
                ),
            ),
            Corridor(
                code = "VE-CO",
                originName = "Venezuela",
                destName = "Colombia",
                placementFeeCapUsd = null,
                placementFeeNote = "No formal placement-fee cap — most " +
                    "Venezuelan migration to Colombia is irregular " +
                    "(humanitarian, post-crisis). Watch for informal " +
                    "smuggling fees + sex-trafficking recruitment.",
                originRegulator = Regulator(
                    name = "Servicio Administrativo de Identificación, " +
                        "Migración y Extranjería (SAIME)",
                    url = "https://www.saime.gob.ve",
                    phone = null,
                ),
                destRegulator = Regulator(
                    name = "Migración Colombia (Permiso por Protección Temporal)",
                    url = "https://www.migracioncolombia.gov.co",
                    phone = "+57 601 605 5454",
                ),
                ngoContacts = listOf(
                    NgoContact("R4V (Regional Refugee and Migrant Response)",
                        "UN-coordinated cross-org response",
                        null, "https://www.r4v.info"),
                    NgoContact("Fundación Renacer",
                        "Anti-trafficking + survivor reintegration in Colombia",
                        "+57 601 549 5424", "https://fundacionrenacer.org"),
                    NgoContact("La Casa de las Migraciones",
                        "Caracas-side info + family-reunion support",
                        null, null),
                ),
            ),
            // ── West Africa → Lebanon (kafala) ────────────────────
            Corridor(
                code = "GH-LB",
                originName = "Ghana",
                destName = "Lebanon",
                placementFeeCapUsd = 200.0,
                placementFeeNote = "Ghanaian Labour Department's MOU with " +
                    "Lebanese General Security caps domestic-worker " +
                    "recruitment fees at ~USD 200; in practice many " +
                    "workers pay 10x via informal sub-agents. " +
                    "Kafala-system risks: passport withholding, wage " +
                    "withholding, restriction of movement.",
                originRegulator = Regulator(
                    name = "Ghana Labour Department",
                    url = "https://labour.gov.gh",
                    phone = "+233 302 666841",
                ),
                destRegulator = Regulator(
                    name = "Lebanese General Security — Foreigners Section",
                    url = "https://www.general-security.gov.lb",
                    phone = "+961 1 425000",
                ),
                ngoContacts = listOf(
                    NgoContact("Anti-Racism Movement (ARM Beirut)",
                        "Migrant Domestic Workers Center + crisis hotline",
                        "+961 70 050 902", "https://www.armlebanon.org"),
                    NgoContact("KAFA (Enough Violence and Exploitation)",
                        "Hotline + shelter + legal services in Lebanon",
                        "+961 3 018 019", "https://www.kafa.org.lb"),
                    NgoContact("Domestic Workers' Union — Ghana",
                        "Pre-departure support + family liaison",
                        null, null),
                ),
            ),
            Corridor(
                code = "NG-LB",
                originName = "Nigeria",
                destName = "Lebanon",
                placementFeeCapUsd = 200.0,
                placementFeeNote = "Same Lebanon-side framework as GH-LB. " +
                    "Nigerian National Agency for the Prohibition of " +
                    "Trafficking in Persons (NAPTIP) tracks high " +
                    "incidence of recruitment fraud + trafficking in " +
                    "this corridor — verify recruiter via NAPTIP " +
                    "before departure.",
                originRegulator = Regulator(
                    name = "NAPTIP (National Agency for Prohibition " +
                        "of Trafficking in Persons)",
                    url = "https://naptip.gov.ng",
                    phone = "+234 9 290 4880",
                ),
                destRegulator = Regulator(
                    name = "Lebanese General Security — Foreigners Section",
                    url = "https://www.general-security.gov.lb",
                    phone = "+961 1 425000",
                ),
                ngoContacts = listOf(
                    NgoContact("Anti-Racism Movement (ARM Beirut)",
                        "Migrant Domestic Workers Center + hotline",
                        "+961 70 050 902", "https://www.armlebanon.org"),
                    NgoContact("Idia Renaissance (Edo State)",
                        "Returnee reintegration + anti-trafficking education",
                        null, null),
                    NgoContact("Devatop Centre for Africa Development",
                        "Pan-African anti-trafficking",
                        null, "https://devatop.org"),
                ),
            ),
            // ── Syria → Europe (post-2011 refugee migration) ──────
            Corridor(
                code = "SY-DE",
                originName = "Syria",
                destName = "Germany",
                placementFeeCapUsd = null,
                placementFeeNote = "Mostly humanitarian/refugee migration, " +
                    "not labour-recruitment. Watch for: smuggler fees " +
                    "(€2-10k typical), false-promise asylum brokers, " +
                    "post-arrival employment fraud preying on refugees " +
                    "without German-language access.",
                originRegulator = Regulator(
                    name = "(no functional Syrian-side regulator post-2011)",
                    url = "https://www.unhcr.org/syria-emergency.html",
                    phone = null,
                ),
                destRegulator = Regulator(
                    name = "Bundesamt für Migration und Flüchtlinge (BAMF)",
                    url = "https://www.bamf.de",
                    phone = "+49 911 943 6390",
                ),
                ngoContacts = listOf(
                    NgoContact("Pro Asyl",
                        "Asylum + refugee rights advocacy in Germany",
                        "+49 69 24 23 14 0", "https://www.proasyl.de"),
                    NgoContact("Caritas Migration und Integration",
                        "Counselling + integration support",
                        null, "https://www.caritas.de/migration"),
                    NgoContact("KOK (German NGO Network Against Trafficking)",
                        "Trafficking-victim support + counselling",
                        "+49 30 263 911 76", "https://www.kok-gegen-menschenhandel.de"),
                ),
            ),
            // ── Asia → Europe (caregivers + factory) ──────────────
            Corridor(
                code = "PH-IT",
                originName = "Philippines",
                destName = "Italy",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Italian Decreto Flussi quota for " +
                    "Filipino caregivers requires zero recruitment " +
                    "fees per the Italy-Philippines bilateral labour " +
                    "agreement (2022 update). Italian Ministry of " +
                    "Labour treats placement-fee charging as illegal " +
                    "intermediation under D.Lgs. 24/2014.",
                originRegulator = Regulator(
                    name = "Department of Migrant Workers (DMW)",
                    url = "https://dmw.gov.ph",
                    phone = "+63-2-8721-1144",
                ),
                destRegulator = Regulator(
                    name = "Italian Ministry of Labour and Social Policies",
                    url = "https://www.lavoro.gov.it",
                    phone = "+39 06 4683 1",
                ),
                ngoContacts = listOf(
                    NgoContact("Caritas Italiana — Migration Office",
                        "Crisis support + legal aid for foreign workers",
                        "+39 06 6617 7501", "https://www.caritas.it"),
                    NgoContact("Filcams CGIL",
                        "Trade union for domestic + service workers",
                        null, "https://www.filcams.cgil.it"),
                    NgoContact("Migrant CARE Italia",
                        "Filipino-community-led legal + social aid",
                        null, null),
                ),
            ),
            Corridor(
                code = "ID-TW",
                originName = "Indonesia",
                destName = "Taiwan",
                placementFeeCapUsd = 1500.0,
                placementFeeNote = "Indonesian Permenaker 9/2019 caps " +
                    "placement-related fees at IDR 19M (~USD 1,500) " +
                    "for Taiwan corridor. Taiwan Direct Hiring Service " +
                    "Center bypasses brokers entirely — workers should " +
                    "verify whether their broker fee was avoidable.",
                originRegulator = Regulator(
                    name = "BP2MI (Indonesian Migrant Worker Protection Agency)",
                    url = "https://bp2mi.go.id",
                    phone = "+62 21 3920 3411",
                ),
                destRegulator = Regulator(
                    name = "Taiwan Ministry of Labour — Direct Hiring Service",
                    url = "https://dhsc.wda.gov.tw",
                    phone = "+886 2 8995 6000",
                ),
                ngoContacts = listOf(
                    NgoContact("TIWA (Taiwan International Workers' Association)",
                        "Multi-corridor migrant worker advocacy in Taiwan",
                        "+886 2 2595 6858", "https://tiwa.org.tw"),
                    NgoContact("Hope Workers Center",
                        "Taoyuan-based legal aid + shelter",
                        "+886 3 425 5416", null),
                    NgoContact("Indonesian Migrant Workers Union — Taiwan chapter",
                        "Peer support + tribunal accompaniment",
                        null, null),
                ),
            ),
            // ── Mekong region (fishing + agriculture) ─────────────
            Corridor(
                code = "MM-TH",
                originName = "Myanmar",
                destName = "Thailand",
                placementFeeCapUsd = 100.0,
                placementFeeNote = "Thai-Myanmar 2003 MOU caps documented-" +
                    "worker fees at THB 3,400 (~USD 100). In practice " +
                    "informal recruitment via brokers (esp. for fishing + " +
                    "agriculture) routinely charges 10-30x. Forced " +
                    "labour in Thai fishing fleets is documented under " +
                    "ILO indicators 4 (debt bondage), 8 (passport " +
                    "withholding), 10 (abusive working conditions).",
                originRegulator = Regulator(
                    name = "Myanmar Ministry of Labour",
                    url = "https://mol.nugmyanmar.org",
                    phone = null,
                ),
                destRegulator = Regulator(
                    name = "Thailand Ministry of Labour — Department of Employment",
                    url = "https://www.doe.go.th",
                    phone = "+66 2 232 1462",
                ),
                ngoContacts = listOf(
                    NgoContact("Migrant Worker Federation (Thailand)",
                        "Cross-border worker rights",
                        null, null),
                    NgoContact("MAP Foundation (Migrant Assistance Program)",
                        "Chiang Mai-based legal aid + community radio",
                        "+66 53 811 202", "https://www.mapfoundationcm.org"),
                    NgoContact("Issara Institute",
                        "Hotline for migrant workers in supply chains",
                        "+66 2 245 2380", "https://www.issarainstitute.org"),
                ),
            ),
            Corridor(
                code = "KH-MY",
                originName = "Cambodia",
                destName = "Malaysia",
                placementFeeCapUsd = 500.0,
                placementFeeNote = "Cambodian MoL fee cap of approximately " +
                    "USD 500 for Malaysia-bound workers (palm oil, " +
                    "manufacturing). Suspension of female domestic " +
                    "worker deployment to Malaysia (since 2011) means " +
                    "any female worker in domestic service via this " +
                    "corridor is irregular — high trafficking risk.",
                originRegulator = Regulator(
                    name = "Cambodian Ministry of Labour and Vocational Training",
                    url = "https://www.mlvt.gov.kh",
                    phone = "+855 23 884 376",
                ),
                destRegulator = Regulator(
                    name = "Department of Labour Peninsular Malaysia (JTKSM)",
                    url = "https://jtksm.mohr.gov.my",
                    phone = "+60 3 8886 5000",
                ),
                ngoContacts = listOf(
                    NgoContact("CARAM Cambodia",
                        "Pre-departure + returnee support; trafficking " +
                            "victim assistance",
                        null, null),
                    NgoContact("Tenaganita Malaysia",
                        "Multi-corridor migrant + trafficking advocacy",
                        "+60 3 7770 3691", "https://tenaganita.net"),
                    NgoContact("Legal Aid Centre, Malaysian Bar",
                        "Pro bono legal aid for foreign workers",
                        "+60 3 2691 3005", "https://www.malaysianbar.org.my"),
                ),
            ),
            // ── East Africa → Gulf (kafala) ───────────────────────
            Corridor(
                code = "ET-LB",
                originName = "Ethiopia",
                destName = "Lebanon",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Ethiopia banned domestic-worker " +
                    "deployment to Lebanon 2013-2018 + has placement-" +
                    "fee restrictions in the 2018-restored framework. " +
                    "Many workers reach Lebanon irregularly via Sudan/" +
                    "Yemen routes, then enter the kafala system on " +
                    "arrival. Highest-incidence trafficking corridor " +
                    "documented by Anti-Slavery International (2024 report).",
                originRegulator = Regulator(
                    name = "Ethiopian Ministry of Labour and Skills",
                    url = "https://mols.gov.et",
                    phone = "+251 11 555 0011",
                ),
                destRegulator = Regulator(
                    name = "Lebanese General Security — Foreigners Section",
                    url = "https://www.general-security.gov.lb",
                    phone = "+961 1 425000",
                ),
                ngoContacts = listOf(
                    NgoContact("Anti-Racism Movement (ARM Beirut)",
                        "Migrant Domestic Workers Center + crisis hotline",
                        "+961 70 050 902", "https://www.armlebanon.org"),
                    NgoContact("KAFA (Enough Violence and Exploitation)",
                        "Hotline + shelter + legal services in Lebanon",
                        "+961 3 018 019", "https://www.kafa.org.lb"),
                    NgoContact("Good Shepherd Sisters — Ethiopia office",
                        "Returnee reintegration + family liaison",
                        null, null),
                ),
            ),
            Corridor(
                code = "KE-SA",
                originName = "Kenya",
                destName = "Saudi Arabia",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Kenya National Employment Authority " +
                    "(NEA) requires zero placement fee for Saudi-bound " +
                    "domestic workers per the 2017 Kenya-Saudi BLA. " +
                    "Repeated Kenyan worker deaths in Saudi Arabia in " +
                    "2022-2024 prompted regulatory review; corridor is " +
                    "high-risk for kafala-system abuses (passport " +
                    "withholding, wage theft, physical violence).",
                originRegulator = Regulator(
                    name = "Kenya National Employment Authority (NEA)",
                    url = "https://nea.go.ke",
                    phone = "+254 20 280 0000",
                ),
                destRegulator = Regulator(
                    name = "Saudi Ministry of Human Resources (Musaned)",
                    url = "https://musaned.com.sa",
                    phone = "19911",
                ),
                ngoContacts = listOf(
                    NgoContact("HAART Kenya (Awareness Against Human Trafficking)",
                        "Hotline + survivor support + legal aid",
                        "+254 780 005 005", "https://haartkenya.org"),
                    NgoContact("Kenya Domestic Workers Council",
                        "Pre-departure + returnee support",
                        null, null),
                    NgoContact("Tawasul (Saudi worker hotline)",
                        "24/7 multi-language complaint line in KSA",
                        "+966 920003343", "https://musaned.com.sa"),
                ),
            ),
            // ── Refugee corridors ─────────────────────────────────
            Corridor(
                code = "AF-IR",
                originName = "Afghanistan",
                destName = "Iran",
                placementFeeCapUsd = null,
                placementFeeNote = "Mostly humanitarian / refugee " +
                    "migration post-2021. Watch for: smuggler fees " +
                    "(typically USD 500-2,000), recruitment into " +
                    "informal construction (no work permit), forced " +
                    "recruitment of Afghan men into Iranian-aligned " +
                    "militias, child-labour exploitation in brick kilns.",
                originRegulator = Regulator(
                    name = "(no functional Afghan-side regulator post-2021)",
                    url = "https://www.unhcr.org/afghanistan",
                    phone = null,
                ),
                destRegulator = Regulator(
                    name = "Iranian Ministry of Cooperatives, Labour & Social Welfare",
                    url = "https://www.mcls.gov.ir",
                    phone = "+98 21 6492",
                ),
                ngoContacts = listOf(
                    NgoContact("UNHCR Iran Operation",
                        "Refugee protection + legal aid",
                        null, "https://www.unhcr.org/ir"),
                    NgoContact("Norwegian Refugee Council Afghanistan",
                        "Cross-border legal counselling for Afghans",
                        null, "https://www.nrc.no/countries/asia/afghanistan"),
                    NgoContact("HAMI Association",
                        "Tehran-based legal aid for Afghan refugees",
                        null, null),
                ),
            ),
            // ── Intra-Africa migration ────────────────────────────
            Corridor(
                code = "ZW-ZA",
                originName = "Zimbabwe",
                destName = "South Africa",
                placementFeeCapUsd = null,
                placementFeeNote = "Most Zimbabwe-South Africa migration " +
                    "is irregular (no formal placement-fee framework). " +
                    "Watch for: ZEP (Zimbabwean Exemption Permit) " +
                    "termination effects, informal mining recruitment, " +
                    "domestic-worker recruitment through informal " +
                    "intermediaries, xenophobic violence patterns.",
                originRegulator = Regulator(
                    name = "Zimbabwe Ministry of Labour and Social Welfare",
                    url = "https://www.publicservice.gov.zw",
                    phone = "+263 24 279 0871",
                ),
                destRegulator = Regulator(
                    name = "South African Department of Employment & Labour",
                    url = "https://www.labour.gov.za",
                    phone = "+27 12 309 4000",
                ),
                ngoContacts = listOf(
                    NgoContact("Lawyers for Human Rights (LHR) South Africa",
                        "Refugee + migrant law clinic",
                        "+27 11 339 1960", "https://www.lhr.org.za"),
                    NgoContact("Migrant Workers Union of South Africa (MIWUSA)",
                        "Cross-border worker organizing",
                        null, null),
                    NgoContact("Scalabrini Centre Cape Town",
                        "Legal + social advocacy for migrants",
                        "+27 21 465 6433", "https://scalabrini.org.za"),
                ),
            ),
            // ── Ukraine → Poland (post-2022) ──────────────────────
            Corridor(
                code = "UA-PL",
                originName = "Ukraine",
                destName = "Poland",
                placementFeeCapUsd = 0.0,
                placementFeeNote = "Polish temporary-protection scheme " +
                    "for Ukrainians is fee-free + work-authorized " +
                    "by default. Recruitment fraud risk is high in " +
                    "informal labour markets (construction, " +
                    "agriculture, care work) — fees charged by " +
                    "intermediaries are recoverable.",
                originRegulator = Regulator(
                    name = "Ukrainian State Employment Service",
                    url = "https://www.dcz.gov.ua",
                    phone = "+380 800 503 753",
                ),
                destRegulator = Regulator(
                    name = "Polish State Labour Inspectorate (PIP)",
                    url = "https://www.pip.gov.pl",
                    phone = "+48 22 391 8240",
                ),
                ngoContacts = listOf(
                    NgoContact("La Strada Poland",
                        "Trafficking + exploitation hotline",
                        "+48 22 628 99 99", "https://www.strada.org.pl"),
                    NgoContact("Helsinki Foundation for Human Rights",
                        "Legal aid for migrants + refugees in Poland",
                        "+48 22 828 10 08", "https://www.hfhr.pl"),
                    NgoContact("Right to Protection (Ukraine-side)",
                        "Pre-departure + cross-border legal aid",
                        null, "https://r2p.org.ua"),
                ),
            ),
        )

        fun byCode(code: String?): Corridor? =
            ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
