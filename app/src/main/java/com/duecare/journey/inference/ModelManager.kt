package com.duecare.journey.inference

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ModelManager"

private val Context.modelPrefs by preferencesDataStore("duecare_model_settings")

/**
 * Manages download + cache of the MediaPipe / LiteRT-LM model file.
 *
 * v0.6 redesign — wires up "everything" the user asked for:
 *
 *   - Six built-in variants (Gemma 4 E2B INT4/INT8, Gemma 4 E4B INT4/INT8,
 *     Gemma 3 1B INT4, Gemma 2 2B INT4 legacy). Worker can switch via
 *     Settings → On-device model.
 *   - Each variant carries a LIST of fallback URLs. Download tries the
 *     primary first; on 404/connect-fail, falls through to the next.
 *     Mirrors include HF Hub primary, GitHub Releases mirror (when we
 *     publish one), and Google Cloud Storage for older Gemma 2 .task
 *     bundles.
 *   - Custom URL override still wins when set — useful for NGO-hosted
 *     mirrors and for fine-tuned variants published elsewhere.
 *   - SHA-256 verification against an optional hash. MediaPipe surfaces
 *     "Invalid Flatbuffer" with no structured error code on
 *     corrupted/partial files; SHA verify catches this before
 *     LlmInference.createFromOptions() blows up.
 *   - Wi-Fi awareness: refuses to start a >1 GB download on metered
 *     networks unless the worker explicitly opts in (the "start anyway"
 *     button in Settings).
 *
 * Privacy invariant: the ONLY outbound network call this app makes
 * (besides the optional opt-in cloud-model routing introduced in v0.6).
 * No telemetry. No background sync. No update check.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val customUrlKey = stringPreferencesKey("custom_model_url")
    private val customShaKey = stringPreferencesKey("custom_model_sha256")
    private val variantKey = stringPreferencesKey("selected_variant")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cached current-variant for synchronous reads. Seeded from
     *  DataStore at init; updated by [setVariant]. */
    private val _activeVariant =
        MutableStateFlow(ModelVariant.GEMMA4_E2B_INT8_LITERTLM)
    val activeVariantFlow: StateFlow<ModelVariant> = _activeVariant.asStateFlow()

    init {
        scope.launch {
            val saved = context.modelPrefs.data
                .map { it[variantKey] ?: ModelVariant.GEMMA4_E2B_INT8_LITERTLM.key }
                .first()
            _activeVariant.value = ModelVariant.fromKey(saved)
        }
    }

    /** Worker-set custom URL. If non-empty, overrides the variant's URL list. */
    val customUrl: Flow<String> = context.modelPrefs.data
        .map { it[customUrlKey].orEmpty() }

    /** Worker-set custom SHA-256 (hex). Empty disables verification. */
    val customSha256: Flow<String> = context.modelPrefs.data
        .map { it[customShaKey].orEmpty() }

    /** Synchronous accessor — current selection is always cached. */
    fun activeVariant(): ModelVariant = _activeVariant.value

    suspend fun setVariant(v: ModelVariant) {
        context.modelPrefs.edit { it[variantKey] = v.key }
        _activeVariant.value = v
    }

    suspend fun setCustomUrl(url: String, sha256: String = "") {
        context.modelPrefs.edit { prefs ->
            prefs[customUrlKey] = url.trim()
            prefs[customShaKey] = sha256.trim()
        }
    }

    suspend fun resolvedUrls(): List<String> {
        val custom = customUrl.first()
        if (custom.isNotBlank()) return listOf(custom)
        return activeVariant().urls
    }

    private suspend fun resolvedSha256(): String {
        val custom = customSha256.first()
        if (custom.isNotBlank()) return custom
        return activeVariant().sha256.orEmpty()
    }

    private fun modelDir(): File =
        File(context.filesDir, "models").apply { mkdirs() }

    /** Path to the cached file for the active variant. */
    fun modelFile(): File = File(modelDir(), activeVariant().fileName)

    val isDownloaded: Boolean
        get() = modelFile().exists() && modelFile().length() > MIN_VALID_SIZE_BYTES

    /** True if the device is currently on an unmetered network
     *  (Wi-Fi, ethernet). False on cellular / metered Wi-Fi. */
    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Stream download progress as a Flow of Progress events.
     *
     *  v0.6: tries each URL in [resolvedUrls] in order. Any non-200 or
     *  connect failure falls through to the next. Final exception is
     *  thrown only after every URL has been tried.
     *
     *  After the file is fully downloaded, runs SHA-256 verification
     *  if a hash is configured. On verify failure, deletes the bad
     *  file and throws.
     */
    fun download(requireUnmetered: Boolean = true): Flow<Progress> = flow {
        val variant: ModelVariant = activeVariant()
        val target = modelFile()
        if (isDownloaded) {
            emit(Progress(target.length(), target.length(), done = true))
            return@flow
        }
        if (requireUnmetered && !isOnUnmeteredNetwork()) {
            throw IllegalStateException(
                "Refusing to start ${variant.expectedSizeBytes / 1024 / 1024} MB " +
                    "download on metered (cellular) network. Switch to Wi-Fi, or " +
                    "tap 'Start anyway' to confirm cellular use."
            )
        }
        val urls = resolvedUrls()
        emit(Progress(0L, variant.expectedSizeBytes, done = false))

        var lastError: Throwable? = null
        for ((i, url) in urls.withIndex()) {
            try {
                Log.i(TAG, "Trying mirror ${i + 1}/${urls.size}: $url")
                downloadFromUrl(
                    url = url,
                    expectedSize = variant.expectedSizeBytes,
                    target = target,
                    emitter = { emit(it) },
                )
                lastError = null
                break
            } catch (e: Throwable) {
                Log.w(TAG, "Mirror ${i + 1} failed (${e.message}). Trying next.")
                lastError = e
                // Clean up any partial file before trying next mirror
                File(target.absolutePath + ".part").delete()
            }
        }
        if (lastError != null) {
            throw IllegalStateException(
                "All ${urls.size} mirror(s) failed for ${variant.displayName}. " +
                    "Last error: ${lastError.message}\n\n" +
                    "Workarounds:\n" +
                    "  • Switch model variant in Settings (try a smaller one).\n" +
                    "  • Paste a direct URL into Settings → Custom URL.\n" +
                    "  • Sideload a .task / .litertlm file via Settings → Use my own model.\n" +
                    "  • Configure a cloud model in Settings → Cloud model.",
                lastError,
            )
        }

        // Verify before atomic rename — corrupted file never reaches
        // the canonical path
        val expectedSha = resolvedSha256()
        val tmp = File(target.absolutePath + ".part")
        if (expectedSha.isNotEmpty() && tmp.exists()) {
            emit(Progress(target.length(), target.length(), done = false, verifying = true))
            val actualSha = sha256Hex(tmp)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                tmp.delete()
                throw IllegalStateException(
                    "SHA-256 mismatch — file may be corrupted or wrong.\n" +
                        "Expected: $expectedSha\nActual:   $actualSha"
                )
            }
            Log.i(TAG, "SHA-256 verified")
        }
        if (tmp.exists() && !tmp.renameTo(target)) {
            throw IllegalStateException("Failed to move downloaded file into place")
        }
        emit(Progress(target.length(), target.length(), done = true))
        Log.i(TAG, "Model download complete: ${target.length()} bytes")
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFromUrl(
        url: String,
        expectedSize: Long,
        target: File,
        emitter: suspend (Progress) -> Unit,
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "DuecareJourney/0.6 (Android)")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("HTTP ${resp.code}")
        }
        val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: expectedSize
        val body = resp.body ?: throw IOException("Empty response body")

        val tmp = File(target.absolutePath + ".part")
        if (tmp.exists()) tmp.delete()
        body.byteStream().use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var written = 0L
                var lastEmit = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    output.write(buf, 0, n)
                    written += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 500) {
                        emitter(Progress(written, total, done = false))
                        lastEmit = now
                    }
                }
            }
        }
    }

    /** Delete the cached model file. Frees disk space. */
    fun deleteCachedModel(): Boolean {
        val f = modelFile()
        return if (f.exists()) f.delete() else true
    }

    /** SHA-256 verify the cached file against the configured hash.
     *  Slow (~5 sec for 1.4 GB on internal storage); call only when
     *  explicitly triggered via Settings → Verify model. */
    suspend fun verifyChecksum(): Boolean {
        val f = modelFile()
        if (!f.exists()) return false
        val expected = resolvedSha256()
        if (expected.isEmpty()) return true
        val actual = sha256Hex(f)
        return actual.equals(expected, ignoreCase = true)
    }

    private fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class Progress(
        val bytesDone: Long,
        val bytesTotal: Long,
        val done: Boolean,
        val verifying: Boolean = false,
    ) {
        val percent: Int get() = if (bytesTotal == 0L) 0 else (bytesDone * 100 / bytesTotal).toInt()
    }

    /**
     * Six built-in variants. Each one has:
     *   - a canonical filename on disk
     *   - a list of mirror URLs tried in order on download
     *   - an expected size (bytes) used for the progress bar before
     *     the server reports a Content-Length
     *
     * Filenames intentionally include the variant key so swapping
     * variants in Settings doesn't clobber a previously-downloaded
     * model — the worker can keep multiple on disk and switch.
     *
     * URL discovery rules:
     *   - litert-community LiteRT-LM bundles use the multi-prefill q4/q8
     *     naming `{Family}-{Size}-it_multi-prefill-seq_q{4|8}_ekv1280.litertlm`.
     *   - Older Gemma 2/3 .task bundles live under MediaPipe's GCS bucket
     *     `storage.googleapis.com/mediapipe-models/llm_inference/...`.
     *   - We also add a generic GitHub Releases mirror under
     *     `github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/...`
     *     which we'll populate post-launch. Keeping the URL in the list
     *     now means a future mirror release becomes a downloadable
     *     fallback without an app update.
     */
    enum class ModelVariant(
        val key: String,
        val displayName: String,
        val familyDescription: String,
        val fileName: String,
        val urls: List<String>,
        val expectedSizeBytes: Long,
        val sha256: String?,
    ) {
        GEMMA4_E2B_INT4_LITERTLM(
            key = "gemma4_e2b_int4",
            displayName = "Gemma 4 E2B (INT4, smallest)",
            familyDescription = "Apache 2.0 · 4-bit · ~750 MB · best for low-RAM phones",
            fileName = "gemma4-e2b-int4.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/Gemma4-E2B-it_multi-prefill-seq_q4_ekv1280.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E2B-it/resolve/main/Gemma4-E2B-it_multi-prefill-seq_q4_ekv1280.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b-int4.litertlm",
            ),
            expectedSizeBytes = 750_000_000L,
            sha256 = null,
        ),
        GEMMA4_E2B_INT8_LITERTLM(
            key = "gemma4_e2b_int8",
            displayName = "Gemma 4 E2B (INT8, recommended)",
            familyDescription = "Apache 2.0 · 8-bit · ~1.5 GB · the v0.6 default",
            fileName = "gemma4-e2b-int8.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/Gemma4-E2B-it_multi-prefill-seq_q8_ekv1280.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E2B-it/resolve/main/Gemma4-E2B-it_multi-prefill-seq_q8_ekv1280.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b-int8.litertlm",
            ),
            expectedSizeBytes = 1_500_000_000L,
            sha256 = null,
        ),
        GEMMA4_E4B_INT4_LITERTLM(
            key = "gemma4_e4b_int4",
            displayName = "Gemma 4 E4B (INT4, higher quality)",
            familyDescription = "Apache 2.0 · 4-bit · ~2.0 GB · needs 6GB+ RAM",
            fileName = "gemma4-e4b-int4.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/Gemma4-E4B-it_multi-prefill-seq_q4_ekv1280.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E4B-it/resolve/main/Gemma4-E4B-it_multi-prefill-seq_q4_ekv1280.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b-int4.litertlm",
            ),
            expectedSizeBytes = 2_000_000_000L,
            sha256 = null,
        ),
        GEMMA4_E4B_INT8_LITERTLM(
            key = "gemma4_e4b_int8",
            displayName = "Gemma 4 E4B (INT8, best quality)",
            familyDescription = "Apache 2.0 · 8-bit · ~3.5 GB · needs 8GB+ RAM",
            fileName = "gemma4-e4b-int8.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/Gemma4-E4B-it_multi-prefill-seq_q8_ekv1280.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E4B-it/resolve/main/Gemma4-E4B-it_multi-prefill-seq_q8_ekv1280.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b-int8.litertlm",
            ),
            expectedSizeBytes = 3_500_000_000L,
            sha256 = null,
        ),
        GEMMA3_1B_TASK(
            key = "gemma3_1b_int4_task",
            displayName = "Gemma 3 1B (INT4, fast fallback)",
            familyDescription = "Apache 2.0 · 4-bit · ~600 MB · fastest first-token latency",
            fileName = "gemma3-1b-it-int4.task",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-3-1b-it/resolve/main/gemma-3-1b-it-int4.task",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma3-1b-it-int4.task",
            ),
            expectedSizeBytes = 600_000_000L,
            sha256 = null,
        ),
        GEMMA2_2B_TASK(
            key = "gemma2_2b_int4_task",
            displayName = "Gemma 2 2B (INT4, legacy gated)",
            familyDescription = "Gemma TOU · 4-bit · ~1.35 GB · usually gated, sideload preferred",
            fileName = "gemma2-2b-it-int4.task",
            urls = listOf(
                "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q4_ekv1280.task",
                "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float16/1/gemma-2b-it-cpu-int4.bin",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma2-2b-it-int4.task",
            ),
            expectedSizeBytes = 1_350_000_000L,
            sha256 = null,
        );

        companion object {
            fun fromKey(k: String): ModelVariant =
                entries.firstOrNull { it.key == k } ?: GEMMA4_E2B_INT8_LITERTLM
        }
    }

    /** Import a model file the worker downloaded externally (e.g.
     *  from Kaggle Models or HF Hub via their browser). Copies the
     *  source into the app's encrypted internal storage with the
     *  expected file name. */
    suspend fun importLocalFile(
        sourceUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
    ): Boolean {
        return try {
            val target = modelFile()
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            target.exists() && target.length() > MIN_VALID_SIZE_BYTES
        } catch (e: Throwable) {
            Log.w(TAG, "importLocalFile failed: $e")
            false
        }
    }

    private companion object {
        const val MIN_VALID_SIZE_BYTES = 100L * 1024 * 1024
    }
}
