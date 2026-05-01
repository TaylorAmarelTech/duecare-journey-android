package com.duecare.journey.harness

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Kotlin port of the 26-doc RAG corpus from
 * `packages/duecare-llm-chat/src/duecare/chat/harness/__init__.py`.
 *
 * STATUS (skeleton): only 4 anchor docs are inline as proof-of-concept.
 * The full 26-doc port lands in v1 MVP via build-time codegen from the
 * Python source-of-truth.
 */
@Singleton
class RagCorpus @Inject constructor() {

    private val docs: List<RagDoc> = listOf(
        RagDoc(
            id = "ilo_c181_art7",
            title = "ILO Convention 181, Article 7 (Private Employment Agencies)",
            source = "ILO C181 Art. 7",
            snippet = "Private employment agencies shall not charge directly " +
                "or indirectly, in whole or in part, any fees or costs to workers.",
        ),
        RagDoc(
            id = "ilo_c189_art9",
            title = "ILO Convention 189, Article 9 (Domestic Workers)",
            source = "ILO C189 Art. 9",
            snippet = "Each Member shall take measures to ensure that domestic " +
                "workers are entitled to keep in their possession their travel " +
                "and identity documents.",
        ),
        RagDoc(
            id = "palermo_protocol_3b",
            title = "Palermo Protocol Art. 3(b) - Consent of the Victim",
            source = "UN Palermo Protocol (2000) Art. 3(b)",
            snippet = "The consent of a victim of trafficking in persons to the " +
                "intended exploitation set forth in subparagraph (a) of this " +
                "article shall be IRRELEVANT where any of the means set forth " +
                "in subparagraph (a) have been used.",
        ),
        RagDoc(
            id = "poea_mc_14_2017",
            title = "POEA MC 14-2017 (HK Domestic Worker Zero Placement Fee)",
            source = "POEA MC 14-2017",
            snippet = "All licensed Philippine recruitment agencies are " +
                "PROHIBITED from charging any placement fee to Filipino " +
                "household service workers (HSWs) deployed to Hong Kong, " +
                "regardless of label.",
        ),
    )

    private val tokenize: (String) -> List<String> = { s ->
        Regex("[a-z0-9]+").findAll(s.lowercase()).map { it.value }.toList()
    }

    private val docTokens = docs.map { it.id to tokenize(it.title + " " + it.snippet) }
    private val docLens = docTokens.map { it.second.size }
    private val avgDocLen = docLens.average().takeIf { !it.isNaN() } ?: 1.0
    private val docFreq: Map<String, Int> = buildMap {
        for ((_, toks) in docTokens) {
            for (t in toks.toSet()) merge(t, 1, Int::plus)
        }
    }
    private val n = docTokens.size

    /** BM25 retrieval of the top-[topK] docs against [query]. */
    fun retrieve(query: String, topK: Int = 5): List<RagDoc> {
        val q = tokenize(query)
        if (q.isEmpty()) return emptyList()
        val scored = docTokens.mapIndexed { i, (_, toks) ->
            val k1 = 1.5
            val b = 0.75
            val tf = toks.groupingBy { it }.eachCount()
            var score = 0.0
            for (qt in q) {
                val df = docFreq[qt] ?: 0
                if (df == 0) continue
                val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5))
                val tfQ = tf[qt] ?: 0
                val norm = tfQ * (k1 + 1) /
                    (tfQ + k1 * (1 - b + b * docLens[i] / avgDocLen))
                score += idf * norm
            }
            score to i
        }.filter { it.first > 0 }.sortedByDescending { it.first }
        return scored.take(topK).map { docs[it.second] }
    }
}
