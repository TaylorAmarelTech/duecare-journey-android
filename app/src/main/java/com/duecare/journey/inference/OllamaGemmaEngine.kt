package com.duecare.journey.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OllamaGemma"
private val JSON = "application/json".toMediaType()

/**
 * v0.6 cloud engine — speaks three on-the-wire shapes against a worker-
 * configured remote endpoint:
 *
 *   - Ollama  : POST {url}/api/generate   { model, prompt, stream:false }
 *               → { "response": "..." }
 *   - OpenAI  : POST {url}/v1/chat/completions
 *               { model, messages:[{role,content}], stream:false }
 *               → { "choices": [{ "message": { "content": "..." } }] }
 *   - HF      : POST {url} (the endpoint URL is the full path)
 *               { "inputs": "...", "parameters": {...} }
 *               → [{ "generated_text": "..." }]
 *
 * For v0.6 we call the non-streaming variant of each and chunk the
 * response client-side to give the UI a typing feel; v0.7 wires the
 * streaming variants (Ollama's NDJSON + OpenAI SSE) through the same
 * Flow contract.
 *
 * Network errors propagate as exceptions so the SmartGemmaEngine
 * fallback chain can route to MediaPipe / stub. The engine never logs
 * the prompt or the response (privacy invariant — even when the worker
 * has opted into a cloud endpoint, we don't add ANOTHER log surface).
 */
@Singleton
class OllamaGemmaEngine @Inject constructor(
    private val cloudPrefs: CloudModelPrefs,
) : GemmaInferenceEngine {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** "Ready" means the worker has configured a URL. Network reach
     *  is checked at call time. */
    override val isReady: Boolean
        get() = lastKnownConfigured

    @Volatile private var lastKnownConfigured: Boolean = false

    /** Refresh the cached "configured" flag from DataStore. Called by
     *  SmartGemmaEngine before routing decisions. */
    suspend fun refresh() {
        lastKnownConfigured = cloudPrefs.isConfigured()
    }

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Flow<String> = flow {
        val full = generate(prompt, maxNewTokens)
        for (chunk in full.chunked(16)) emit(chunk)
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        val cfg = cloudPrefs.snapshot()
        check(cfg.isConfigured) {
            "Cloud model not configured — set URL in Settings → Cloud model"
        }
        return when (cfg.format) {
            CloudModelPrefs.Format.OLLAMA -> callOllama(cfg, prompt, maxNewTokens)
            CloudModelPrefs.Format.OPENAI -> callOpenAI(cfg, prompt, maxNewTokens)
            CloudModelPrefs.Format.HF -> callHuggingFace(cfg, prompt, maxNewTokens)
        }
    }

    private fun callOllama(
        cfg: CloudModelPrefs.Snapshot,
        prompt: String,
        maxNewTokens: Int,
    ): String {
        val base = cfg.url.trimEnd('/')
        val url = if (base.endsWith("/api/generate") || base.endsWith("/api/chat")) base
        else "$base/api/generate"
        val body = JSONObject().apply {
            put("model", cfg.modelName)
            put("prompt", prompt)
            put("stream", false)
            put("options", JSONObject().apply {
                put("num_predict", maxNewTokens)
                put("temperature", 0.7)
                put("top_k", 40)
                put("top_p", 0.95)
            })
        }.toString()
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("User-Agent", "DuecareJourney/0.6 (Android)")
            .also { if (cfg.apiKey.isNotBlank()) it.header("Authorization", "Bearer ${cfg.apiKey}") }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Ollama call failed: HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val obj = JSONObject(raw)
            return obj.optString("response").ifBlank {
                obj.optJSONObject("message")?.optString("content").orEmpty()
            }
        }
    }

    private fun callOpenAI(
        cfg: CloudModelPrefs.Snapshot,
        prompt: String,
        maxNewTokens: Int,
    ): String {
        val base = cfg.url.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base
        else "$base/v1/chat/completions"
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }
        val body = JSONObject().apply {
            put("model", cfg.modelName)
            put("messages", messages)
            put("max_tokens", maxNewTokens)
            put("temperature", 0.7)
            put("top_p", 0.95)
            put("stream", false)
        }.toString()
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("User-Agent", "DuecareJourney/0.6 (Android)")
            .also { if (cfg.apiKey.isNotBlank()) it.header("Authorization", "Bearer ${cfg.apiKey}") }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("OpenAI-compatible call failed: HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val choices = JSONObject(raw).optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            return choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }

    private fun callHuggingFace(
        cfg: CloudModelPrefs.Snapshot,
        prompt: String,
        maxNewTokens: Int,
    ): String {
        val body = JSONObject().apply {
            put("inputs", prompt)
            put("parameters", JSONObject().apply {
                put("max_new_tokens", maxNewTokens)
                put("temperature", 0.7)
                put("top_k", 40)
                put("top_p", 0.95)
                put("return_full_text", false)
            })
        }.toString()
        val req = Request.Builder()
            .url(cfg.url)
            .post(body.toRequestBody(JSON))
            .header("User-Agent", "DuecareJourney/0.6 (Android)")
            .also { if (cfg.apiKey.isNotBlank()) it.header("Authorization", "Bearer ${cfg.apiKey}") }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HF Inference call failed: HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val arr = try { JSONArray(raw) } catch (_: Throwable) { null }
            if (arr != null && arr.length() > 0) {
                return arr.optJSONObject(0)?.optString("generated_text").orEmpty()
            }
            // Some HF endpoints return a single object instead of an array
            return JSONObject(raw).optString("generated_text")
        }
    }
}
