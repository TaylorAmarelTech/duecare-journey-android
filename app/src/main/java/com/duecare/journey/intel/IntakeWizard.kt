package com.duecare.journey.intel

import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.JourneyStage

/**
 * Static script for the guided intake wizard.
 *
 * Each [Question] maps an exploitation-pattern category to a concrete
 * journal-entry shape (stage + kind + title prefix). The order is
 * informed by the harness research: recruiter + fees go first because
 * those are the most-recoverable forms of evidence; conditions and
 * incidents come later because they require more context.
 *
 * The wizard is not a model — it's a deterministic script. Gemma is
 * available via the Advice tab if a worker needs free-form help, but
 * the intake itself runs even with no model present.
 */
object IntakeWizard {

    enum class AnswerKind {
        SHORT_TEXT,
        LONG_TEXT,
        CURRENCY_AMOUNT,
        DATE,
        YES_NO,
        SKIP_OK,
    }

    data class Question(
        val id: String,
        val category: String,
        val prompt: String,
        val helperText: String,
        val placeholder: String,
        val answerKind: AnswerKind,
        val journalStage: JourneyStage,
        val journalKind: EntryKind,
        val journalTitlePrefix: String,
        val skipAllowed: Boolean = true,
    )

    val ALL: List<Question> = listOf(
        Question(
            id = "recruiter-name",
            category = "Recruiter",
            prompt = "What's the name of the person or agency that recruited you?",
            helperText = "If they used multiple names (a sub-agent in your village + " +
                "a registered agency in the city), name both.",
            placeholder = "e.g. Maria from XYZ Manpower, recruited via auntie Linda",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.NOTE,
            journalTitlePrefix = "Recruiter:",
        ),
        Question(
            id = "recruiter-license",
            category = "Recruiter",
            prompt = "Do they have a license number? If so, what is it?",
            helperText = "POEA-licensed agencies in the Philippines, BMET RL number " +
                "in Bangladesh, BP2MI license in Indonesia, etc. If they say 'we " +
                "don't need one' that's itself important to record.",
            placeholder = "e.g. POEA-105-LB-040818-RC",
            answerKind = AnswerKind.SHORT_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.NOTE,
            journalTitlePrefix = "Recruiter license:",
        ),
        Question(
            id = "fees-paid",
            category = "Fees",
            prompt = "How much have you paid the recruiter so far, and for what?",
            helperText = "Include all fees: training, medical, processing, placement, " +
                "passport, visa. Even fees they call 'free' but ask you to pay anyway.",
            placeholder = "e.g. PHP 50,000 training fee + PHP 10,000 medical, paid in cash",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.EXPENSE,
            journalTitlePrefix = "Fees paid:",
        ),
        Question(
            id = "loan-terms",
            category = "Fees",
            prompt = "Did you take a loan to pay any of these fees? If so, what are the terms?",
            helperText = "Interest rate, monthly payment, who the lender is, whether " +
                "your future salary is guaranteed against the loan.",
            placeholder = "e.g. PHP 80,000 loan from agency partner at 5% per month",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.EXPENSE,
            journalTitlePrefix = "Loan terms:",
        ),
        Question(
            id = "contract-signed",
            category = "Contract",
            prompt = "Have you signed a contract? Is it in a language you read well?",
            helperText = "If they only gave you a contract in English/Arabic/Cantonese " +
                "and yours is different, that matters. Photograph the contract.",
            placeholder = "e.g. Yes, English only. I read English okay.",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.DOCUMENT,
            journalTitlePrefix = "Contract:",
        ),
        Question(
            id = "wage-promise",
            category = "Contract",
            prompt = "What wage did they promise, in what currency, and how often will you be paid?",
            helperText = "Important: if your contract says one thing and the recruiter " +
                "says another, write down both.",
            placeholder = "e.g. HKD 4,870/month, paid into HSBC account on the 7th",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.NOTE,
            journalTitlePrefix = "Promised wage:",
        ),
        Question(
            id = "passport-status",
            category = "Documents",
            prompt = "Where is your passport right now?",
            helperText = "ANY answer other than 'with me' is important to record. " +
                "Withholding of identity documents is the #8 ILO indicator of " +
                "forced labour and a primary trafficking marker.",
            placeholder = "e.g. with me / with the recruiter for safekeeping / not yet issued",
            answerKind = AnswerKind.SHORT_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.DOCUMENT,
            journalTitlePrefix = "Passport status:",
        ),
        Question(
            id = "destination-address",
            category = "Destination",
            prompt = "Do you know the address where you'll be working? The employer's name?",
            helperText = "Many trafficking cases involve workers told 'you'll find out " +
                "when you arrive'. If you don't know, that's important to record now.",
            placeholder = "e.g. Mr. Wong, address in Causeway Bay, full address TBD",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.NOTE,
            journalTitlePrefix = "Destination employer:",
        ),
        Question(
            id = "communication-restrictions",
            category = "Conditions",
            prompt = "Are you free to call your family and friends back home whenever you want?",
            helperText = "Restriction of communication is the #6 ILO indicator. " +
                "Recruiters who insist on screening your calls before departure " +
                "often continue to do so after arrival.",
            placeholder = "e.g. Yes, no restrictions / Recruiter takes my phone in evenings",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.NOTE,
            journalTitlePrefix = "Communication freedom:",
        ),
        Question(
            id = "any-coercion",
            category = "Conditions",
            prompt = "Has anyone — recruiter, sub-agent, employer rep — pressured, threatened, or hurt you?",
            helperText = "Threats of deportation, threats of harm to your family, even " +
                "loud aggressive pressure, are the #1 and #2 ILO indicators. " +
                "Even small incidents matter — they form a pattern.",
            placeholder = "e.g. None / Recruiter threatened to keep my deposit if I quit",
            answerKind = AnswerKind.LONG_TEXT,
            journalStage = JourneyStage.PRE_DEPARTURE,
            journalKind = EntryKind.INCIDENT,
            journalTitlePrefix = "Pressure / threats:",
        ),
    )

    /** Convert a worker's answer to a journal-entry payload. */
    data class EntryDraft(
        val stage: JourneyStage,
        val kind: EntryKind,
        val title: String,
        val body: String,
    )

    fun toDraft(question: Question, answer: String): EntryDraft? {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return null
        return EntryDraft(
            stage = question.journalStage,
            kind = question.journalKind,
            title = "${question.journalTitlePrefix} ${trimmed.take(80)}",
            body = "Q: ${question.prompt}\n\nA: $trimmed",
        )
    }
}
