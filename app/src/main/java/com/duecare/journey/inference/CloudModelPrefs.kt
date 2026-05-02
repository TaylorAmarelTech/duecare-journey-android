package com.duecare.journey.inference

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudPrefs by preferencesDataStore("duecare_cloud_model")

/**
 * v0.6: persistent cloud-model configuration.
 *
 * Lets the worker (or NGO sysadmin staging the device) point the chat
 * surface at a remote Gemma 4 endpoint when the on-device model has
 * not been downloaded yet — the most common reason being that the
 * litert-community URL we hard-coded in v0.5 returns 404 because HF
 * renamed the file.
 *
 * Three supported shapes:
 *
 *   1. Ollama (`/api/generate` + `/api/chat`) — `format = "ollama"`.
 *      Pointed at a local Ollama server reachable from the phone (e.g.
 *      a laptop on the same LAN with `ollama serve`) or a hosted
 *      Ollama-compatible endpoint. Bearer token optional.
 *   2. OpenAI-compatible chat completions — `format = "openai"`. Works
 *      against vLLM, LM Studio, OpenRouter, Together, Groq, Fireworks,
 *      anything that follows the `/v1/chat/completions` shape.
 *   3. Hugging Face Inference Endpoint (text-generation) — `format =
 *      "hf"`. POSTs to the endpoint URL with `{"inputs": ...,
 *      "parameters": {...}}`.
 *
 * Privacy note: enabling cloud routing means the worker's prompts
 * leave the device. The Settings UI surfaces this in plain language
 * before saving. The on-device path remains the default; cloud is
 * opt-in for testing or when a worker explicitly trusts the operator
 * of the remote endpoint.
 */
@Singleton
class CloudModelPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val urlKey = stringPreferencesKey("cloud_model_url")
    private val keyKey = stringPreferencesKey("cloud_model_api_key")
    private val modelKey = stringPreferencesKey("cloud_model_name")
    private val formatKey = stringPreferencesKey("cloud_model_format")

    val url: Flow<String> = context.cloudPrefs.data.map { it[urlKey].orEmpty() }
    val apiKey: Flow<String> = context.cloudPrefs.data.map { it[keyKey].orEmpty() }
    val modelName: Flow<String> = context.cloudPrefs.data.map { it[modelKey] ?: "gemma4:e2b" }
    val format: Flow<Format> = context.cloudPrefs.data.map {
        Format.fromKey(it[formatKey] ?: Format.OLLAMA.key)
    }

    suspend fun isConfigured(): Boolean = url.first().isNotBlank()

    suspend fun snapshot(): Snapshot = Snapshot(
        url = url.first(),
        apiKey = apiKey.first(),
        modelName = modelName.first(),
        format = format.first(),
    )

    suspend fun save(
        url: String,
        apiKey: String,
        modelName: String,
        format: Format,
    ) {
        context.cloudPrefs.edit { prefs ->
            prefs[urlKey] = url.trim()
            prefs[keyKey] = apiKey.trim()
            prefs[modelKey] = modelName.trim().ifBlank { "gemma4:e2b" }
            prefs[formatKey] = format.key
        }
    }

    suspend fun clear() {
        context.cloudPrefs.edit { it.clear() }
    }

    enum class Format(val key: String, val displayName: String) {
        OLLAMA("ollama", "Ollama (local or hosted)"),
        OPENAI("openai", "OpenAI-compatible chat completions"),
        HF("hf", "HuggingFace Inference Endpoint");

        companion object {
            fun fromKey(k: String): Format =
                entries.firstOrNull { it.key == k } ?: OLLAMA
        }
    }

    data class Snapshot(
        val url: String,
        val apiKey: String,
        val modelName: String,
        val format: Format,
    ) {
        val isConfigured: Boolean get() = url.isNotBlank()
    }
}
