package com.duecare.journey.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRT-backed implementation of [GemmaInferenceEngine].
 *
 * **STATUS: skeleton.** The real implementation requires the LiteRT
 * Gemma 4 E2B `.task` bundle (produced by AI Edge Torch from
 * `google/gemma-4-e2b-it`). The conversion + bundling step is part of
 * the v1 MVP build — we'll publish the conversion script alongside the
 * Kaggle bench-and-tune notebook (currently outputs GGUF for desktop /
 * llama.cpp).
 *
 * Privacy invariant: the FIRST-LAUNCH model download is the only
 * outbound network call this class makes. The download URL is
 * hard-coded to a single host; any other URL passed in fails the
 * [verifyDownloadHost] check.
 *
 * Threading: all inference runs on [Dispatchers.IO]. The native
 * LiteRT runtime is thread-safe for a single interpreter, but we
 * serialize calls in the v1 MVP — multi-stream batching is a v2 item.
 */
@Singleton
class LiteRTGemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : GemmaInferenceEngine {

    private val modelCacheDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    private val modelFile: File
        get() = File(modelCacheDir, "gemma-4-e2b-it.task")

    @Volatile
    private var interpreter: Any? = null  // org.tensorflow.lite.Interpreter when v1 lands

    override val isReady: Boolean
        get() = interpreter != null

    /**
     * Download the LiteRT model bundle if it's not already present.
     * Called once on first app launch (over Wi-Fi, prompted, never
     * silent). Subsequent launches reuse the cached file.
     */
    suspend fun ensureModelDownloaded(progress: (Long, Long) -> Unit): Boolean {
        if (modelFile.exists() && modelFile.length() > 0) return true
        // TODO(v1): implement HTTP range-resumable download from
        // hardcoded HF Hub mirror; verify SHA-256 against bundled
        // manifest; reject any redirects to non-allowlisted hosts.
        return false
    }

    /**
     * Load the interpreter from disk into memory. Idempotent.
     */
    suspend fun loadModel() {
        if (interpreter != null) return
        // TODO(v1):
        // val opts = Interpreter.Options().apply {
        //     setNumThreads(4)
        //     // NNAPI delegate for hardware acceleration on API 27+
        //     addDelegate(NnApiDelegate())
        // }
        // interpreter = Interpreter(modelFile, opts)
    }

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Flow<String> = flow {
        check(isReady) {
            "Model not loaded. Call loadModel() first."
        }
        // TODO(v1): tokenize prompt, run interpreter loop, decode
        // each token and `emit(token)`. Stop on EOS or maxNewTokens.
        emit("[stub: real inference lands in v1 MVP]")
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        val sb = StringBuilder()
        streamGenerate(prompt, maxNewTokens).collect { sb.append(it) }
        return sb.toString()
    }

    private fun verifyDownloadHost(url: String): Boolean {
        // Allowlist: HF Hub (where we'll publish the .task bundle).
        return url.startsWith("https://huggingface.co/") ||
            url.startsWith("https://cdn-lfs.huggingface.co/")
    }
}
