package com.duecare.journey.inference

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Stub implementation of [GemmaInferenceEngine] for unit/UI tests
 * and for the v1 skeleton build. Returns canned text that includes
 * the right shape (statute citations + ILO references + NGO hotline)
 * so the UI flow can be developed before the real LiteRT model is
 * available.
 */
class StubGemmaEngine @Inject constructor() : GemmaInferenceEngine {

    override val isReady: Boolean = true

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Flow<String> = flow {
        for (chunk in CANNED_RESPONSE.chunked(8)) {
            delay(40)
            emit(chunk)
        }
    }

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        return CANNED_RESPONSE
    }

    private companion object {
        const val CANNED_RESPONSE =
            "This arrangement triggers ILO Forced Labour Indicator 4 " +
            "(debt bondage) and Indicator 8 (withholding of identity " +
            "documents). The 68% APR violates HK Money Lenders Ord. " +
            "Cap. 163 §24 (>60% is automatically extortionate). The " +
            "salary-deduction-to-lender structure is prohibited under " +
            "HK Employment Ord. Cap. 57 §32 and ILO C095 Art. 9.\n\n" +
            "POEA Memorandum Circular 14-2017 sets a zero placement fee " +
            "for the Philippines→Hong Kong domestic-worker corridor; " +
            "any fee charged to you is a regulatory violation.\n\n" +
            "I cannot help structure this arrangement. Please contact:\n" +
            "  • POEA Anti-Illegal Recruitment Branch +63-2-8721-1144\n" +
            "  • Mission for Migrant Workers Hong Kong +852-2522-8264"
    }
}
