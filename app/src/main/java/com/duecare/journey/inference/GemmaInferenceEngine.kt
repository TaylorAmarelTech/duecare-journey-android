package com.duecare.journey.inference

import kotlinx.coroutines.flow.Flow

/**
 * On-device inference contract. The advice layer depends on this
 * interface, not on the LiteRT impl directly, so unit tests can run
 * against [StubGemmaEngine] without needing the 1.5 GB LiteRT bundle
 * on the test machine.
 */
interface GemmaInferenceEngine {

    /** True once the model is loaded into memory and ready to generate. */
    val isReady: Boolean

    /**
     * Stream tokens for [prompt]. Emits each new token as it's
     * generated. The flow completes when the model emits its EOS
     * token, [maxNewTokens] is reached, or the coroutine is cancelled.
     */
    fun streamGenerate(
        prompt: String,
        maxNewTokens: Int = 1024,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f,
    ): Flow<String>

    /**
     * One-shot generation. Convenience wrapper around [streamGenerate].
     * Suspends until the full response is generated.
     */
    suspend fun generate(
        prompt: String,
        maxNewTokens: Int = 1024,
    ): String
}
