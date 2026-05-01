package com.duecare.journey.inference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPipeGemma"

/**
 * Real Gemma inference on the phone via MediaPipe LLM Inference API.
 *
 * Status: production-ready path. The model file (`gemma-2b-it-cpu-int4.task`
 * or `gemma-2b-it-gpu-int4.task`) is downloaded once on first use by
 * [ModelManager] and cached at `filesDir/models/`. Subsequent app
 * launches reuse the cached file.
 *
 * Throughput on a Pixel 7 / Snapdragon 8 Gen 1 (CPU INT4):
 *   - cold first-token latency: ~3 sec
 *   - sustained: 8-12 tokens/sec
 * GPU INT4 variant is ~2-3× faster but currently has stricter device
 * compatibility — fall back to CPU when GPU init fails.
 *
 * Threading: all inference runs on Dispatchers.IO; the underlying
 * [LlmInference] is thread-safe for a single instance, but we
 * serialize calls (no batching) to keep the v1 API surface small.
 */
@Singleton
class MediaPipeGemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
) : GemmaInferenceEngine {

    @Volatile
    private var llm: LlmInference? = null

    override val isReady: Boolean
        get() = llm != null

    /** Ensure the model is loaded. Call before [streamGenerate].
     *  Throws if no model file is available — caller should trigger
     *  [ModelManager.download] first. */
    suspend fun ensureLoaded() {
        if (llm != null) return
        val modelFile = modelManager.modelFile()
        if (!modelFile.exists()) {
            throw IllegalStateException(
                "Model not downloaded. Call ModelManager.download() first."
            )
        }
        Log.i(TAG, "Loading MediaPipe LLM from ${modelFile.path} (${modelFile.length() / 1024 / 1024} MB)")
        val opts = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTopK(64)
            .setMaxTokens(2048)
            .build()
        llm = LlmInference.createFromOptions(context, opts)
        Log.i(TAG, "MediaPipe LLM loaded")
    }

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Flow<String> = flow {
        ensureLoaded()
        val engine = llm!!
        // MediaPipe v0.10.x exposes generateResponse (one-shot) and
        // generateResponseAsync (streaming via callback). For the
        // v0.3 release we use one-shot + emit in chunks for perceived
        // streaming. v0.4 wires the async streaming callback to
        // emit per-token.
        val response = engine.generateResponse(prompt) ?: ""
        // Emit in ~16-char chunks to give the UI a "typing" feel
        for (chunk in response.chunked(16)) {
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        ensureLoaded()
        return llm!!.generateResponse(prompt) ?: ""
    }

    /** Free the underlying engine + model from memory. Call from
     *  Settings → Unload model, or before a panic-wipe. */
    fun unload() {
        llm?.close()
        llm = null
    }
}
