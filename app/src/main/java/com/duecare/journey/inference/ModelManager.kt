package com.duecare.journey.inference

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duecare.journey.BuildConfig
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Cached current-variant for synchronous reads. Seeded from
     *  DataStore at init; updated by [setVariant]. */
    private val _activeVariant =
        MutableStateFlow(ModelVariant.fromKey(BuildConfig.DEFAULT_MODEL_KEY))
    val activeVariantFlow: StateFlow<ModelVariant> = _activeVariant.asStateFlow()

    init {
        scope.launch {
            val saved = context.modelPrefs.data
                .map { it[variantKey] ?: BuildConfig.DEFAULT_MODEL_KEY }
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
        val variant = activeVariant()
        return (discoverHuggingFaceUrls(variant) + variant.urls).distinct()
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
            emit(
                Progress(
                    target.length(),
                    target.length(),
                    done = true,
                    status = "Model already downloaded",
                )
            )
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
        if (urls.isEmpty()) {
            throw IllegalStateException("No model download URLs are configured for ${variant.displayName}")
        }
        emit(
            Progress(
                0L,
                variant.expectedSizeBytes,
                done = false,
                status = "Resolved ${urls.size} download source(s)",
                mirrorCount = urls.size,
            )
        )

        var lastError: Throwable? = null
        for ((i, url) in urls.withIndex()) {
            try {
                Log.i(TAG, "Trying mirror ${i + 1}/${urls.size}: $url")
                emit(
                    Progress(
                        0L,
                        variant.expectedSizeBytes,
                        done = false,
                        status = "Connecting to ${hostFromUrl(url)}",
                        mirrorIndex = i + 1,
                        mirrorCount = urls.size,
                        sourceHost = hostFromUrl(url),
                    )
                )
                downloadFromUrl(
                    url = url,
                    expectedSize = variant.expectedSizeBytes,
                    target = target,
                    mirrorIndex = i + 1,
                    mirrorCount = urls.size,
                    emitter = { emit(it) },
                )
                lastError = null
                break
            } catch (e: Throwable) {
                Log.w(TAG, "Mirror ${i + 1} failed (${e.message}). Trying next.")
                lastError = e
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
            emit(
                Progress(
                    tmp.length(),
                    tmp.length(),
                    done = false,
                    verifying = true,
                    status = "Verifying SHA-256",
                )
            )
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
        if (tmp.exists() && tmp.length() <= MIN_VALID_SIZE_BYTES) {
            val badSize = tmp.length()
            tmp.delete()
            throw IllegalStateException(
                "Downloaded file is too small to be a LiteRT model ($badSize bytes)"
            )
        }
        if (target.exists()) target.delete()
        if (tmp.exists() && !tmp.renameTo(target)) {
            throw IllegalStateException("Failed to move downloaded file into place")
        }
        File(target.absolutePath + ".part.url").delete()
        emit(
            Progress(
                target.length(),
                target.length(),
                done = true,
                status = "Model download complete",
            )
        )
        Log.i(TAG, "Model download complete: ${target.length()} bytes")
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFromUrl(
        url: String,
        expectedSize: Long,
        target: File,
        mirrorIndex: Int,
        mirrorCount: Int,
        emitter: suspend (Progress) -> Unit,
        allowResume: Boolean = true,
    ) {
        val tmp = File(target.absolutePath + ".part")
        val partialUrlFile = File(target.absolutePath + ".part.url")
        val partialUrl = partialUrlFile.takeIf { it.exists() }?.readText().orEmpty()
        if (tmp.exists() && partialUrl.isNotBlank() && partialUrl != url) {
            tmp.delete()
            partialUrlFile.delete()
        }
        val resumeFrom = if (
            allowResume &&
            tmp.exists() &&
            partialUrl == url &&
            tmp.length() > 0L
        ) {
            tmp.length()
        } else {
            0L
        }
        val req = Request.Builder().url(url)
            .header("User-Agent", "DuecareJourney/0.9 (Android)")
            .apply {
                if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-")
            }
            .build()

        http.newCall(req).execute().use { resp ->
            if (resp.code == 416 && resumeFrom > 0L) {
                tmp.delete()
                partialUrlFile.delete()
                return downloadFromUrl(
                    url = url,
                    expectedSize = expectedSize,
                    target = target,
                    mirrorIndex = mirrorIndex,
                    mirrorCount = mirrorCount,
                    emitter = emitter,
                    allowResume = false,
                )
            }
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}")
            }
            val append = resumeFrom > 0L && resp.code == 206
            if (!append) tmp.delete()
            partialUrlFile.writeText(url)

            val body = resp.body ?: throw IOException("Empty response body")
            val total = contentRangeTotal(resp.header("Content-Range"))
                ?: body.contentLength().takeIf { it > 0 }?.let {
                    if (append) resumeFrom + it else it
                }
                ?: expectedSize
            val initialBytes = if (append) resumeFrom else 0L
            emitter(
                Progress(
                    initialBytes,
                    total,
                    done = false,
                    status = if (append) {
                        "Resuming from ${mb(initialBytes)} MB on ${hostFromUrl(url)}"
                    } else {
                        "Downloading from ${hostFromUrl(url)}"
                    },
                    mirrorIndex = mirrorIndex,
                    mirrorCount = mirrorCount,
                    sourceHost = hostFromUrl(url),
                )
            )
            body.byteStream().use { input ->
                FileOutputStream(tmp, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var written = initialBytes
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 500) {
                            emitter(
                                Progress(
                                    written,
                                    total,
                                    done = false,
                                    status = "Downloading from ${hostFromUrl(url)}",
                                    mirrorIndex = mirrorIndex,
                                    mirrorCount = mirrorCount,
                                    sourceHost = hostFromUrl(url),
                                )
                            )
                            lastEmit = now
                        }
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

    private fun discoverHuggingFaceUrls(variant: ModelVariant): List<String> {
        if (variant.huggingFaceRepos.isEmpty()) return emptyList()
        val urls = mutableListOf<String>()
        for (repo in variant.huggingFaceRepos) {
            try {
                val apiUrl = "https://huggingface.co/api/models/$repo?blobs=false"
                val req = Request.Builder().url(apiUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "DuecareJourney/0.9 (Android)")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "HF manifest lookup failed for $repo: HTTP ${resp.code}")
                        return@use
                    }
                    val body = resp.body?.string().orEmpty()
                    val siblings = JSONObject(body).optJSONArray("siblings") ?: return@use
                    val files = (0 until siblings.length())
                        .mapNotNull { i ->
                            siblings.optJSONObject(i)
                                ?.optString("rfilename")
                                ?.takeIf { isModelArtifactName(it) }
                        }
                        .sortedWith(
                            compareBy<String> { rankHuggingFaceFile(it, variant) }
                                .thenBy { it.length }
                                .thenBy { it.lowercase() }
                        )
                    files.forEach { path ->
                        urls += "https://huggingface.co/$repo/resolve/main/${encodePath(path)}"
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "HF manifest lookup failed for $repo: ${e.message}")
            }
        }
        return urls
    }

    private fun isModelArtifactName(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".task") || lower.endsWith(".litertlm")
    }

    private fun rankHuggingFaceFile(path: String, variant: ModelVariant): Int {
        val lower = path.lowercase()
        val exact = variant.preferredHuggingFaceFiles
            .indexOfFirst { it.equals(path, ignoreCase = true) }
        if (exact >= 0) return exact
        var score = 100
        if (lower.endsWith(".task")) score += variant.taskPreferencePenalty
        if (lower.endsWith(".litertlm")) score += variant.litertLmPreferencePenalty
        if (lower.contains("web")) score -= 8
        if (lower.contains("qualcomm") || lower.contains("qcs") || lower.contains("sm8750")) score += 50
        if (!lower.contains("gemma")) score += 100
        return score
    }

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { segment ->
            android.net.Uri.encode(segment) ?: segment
        }

    private fun contentRangeTotal(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val total = value.substringAfter("/", missingDelimiterValue = "")
        return total.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun hostFromUrl(url: String): String =
        android.net.Uri.parse(url).host ?: "download source"

    private fun mb(bytes: Long): Long = bytes / 1024 / 1024

    data class Progress(
        val bytesDone: Long,
        val bytesTotal: Long,
        val done: Boolean,
        val verifying: Boolean = false,
        val status: String = "",
        val mirrorIndex: Int = 0,
        val mirrorCount: Int = 0,
        val sourceHost: String = "",
    ) {
        val percent: Int
            get() = if (bytesTotal <= 0L) {
                0
            } else {
                ((bytesDone.coerceAtLeast(0L) * 100) / bytesTotal)
                    .coerceIn(0L, 100L)
                    .toInt()
            }
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
     *   - On each download, the app first checks the public HF repository
     *     manifest for current `.task` / `.litertlm` filenames, then ranks
     *     them against the variant's preferred filenames. This prevents a
     *     renamed HF artifact from breaking every worker's first launch.
     *   - Pinned URLs remain as deterministic fallbacks for offline docs,
     *     release mirrors, and older Gemma 2/3 `.task` bundles.
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
        val huggingFaceRepos: List<String> = emptyList(),
        val preferredHuggingFaceFiles: List<String> = emptyList(),
        val taskPreferencePenalty: Int = 5,
        val litertLmPreferencePenalty: Int = 10,
        val expectedSizeBytes: Long,
        val sha256: String?,
    ) {
        GEMMA4_E2B_INT4_LITERTLM(
            key = "gemma4_e2b_int4",
            displayName = "Gemma 4 E2B (web task, smaller)",
            familyDescription = "Apache 2.0 - LiteRT task - about 2 GB - best first download",
            fileName = "gemma4-e2b-web.task",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b-web.task",
            ),
            huggingFaceRepos = listOf(
                "litert-community/gemma-4-E2B-it-litert-lm",
                "litert-community/gemma-4-E2B-it",
            ),
            preferredHuggingFaceFiles = listOf(
                "gemma-4-E2B-it-web.task",
                "gemma-4-E2B-it.litertlm",
            ),
            taskPreferencePenalty = 0,
            litertLmPreferencePenalty = 8,
            expectedSizeBytes = 2_000_000_000L,
            sha256 = null,
        ),
        GEMMA4_E2B_INT8_LITERTLM(
            key = "gemma4_e2b_int8",
            displayName = "Gemma 4 E2B (LiteRT-LM, recommended)",
            familyDescription = "Apache 2.0 - LiteRT-LM - about 2.6 GB - stronger local model",
            fileName = "gemma4-e2b.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b.litertlm",
            ),
            huggingFaceRepos = listOf(
                "litert-community/gemma-4-E2B-it-litert-lm",
                "litert-community/gemma-4-E2B-it",
            ),
            preferredHuggingFaceFiles = listOf(
                "gemma-4-E2B-it.litertlm",
                "gemma-4-E2B-it-web.task",
            ),
            taskPreferencePenalty = 8,
            litertLmPreferencePenalty = 0,
            expectedSizeBytes = 2_600_000_000L,
            sha256 = null,
        ),
        GEMMA4_E4B_INT4_LITERTLM(
            key = "gemma4_e4b_int4",
            displayName = "Gemma 4 E4B (web task)",
            familyDescription = "Apache 2.0 - LiteRT task - large download - needs 6GB+ RAM",
            fileName = "gemma4-e4b-web.task",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task",
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b-web.task",
            ),
            huggingFaceRepos = listOf(
                "litert-community/gemma-4-E4B-it-litert-lm",
                "litert-community/gemma-4-E4B-it",
            ),
            preferredHuggingFaceFiles = listOf(
                "gemma-4-E4B-it-web.task",
                "gemma-4-E4B-it.litertlm",
            ),
            taskPreferencePenalty = 0,
            litertLmPreferencePenalty = 8,
            expectedSizeBytes = 3_500_000_000L,
            sha256 = null,
        ),
        GEMMA4_E4B_INT8_LITERTLM(
            key = "gemma4_e4b_int8",
            displayName = "Gemma 4 E4B (LiteRT-LM, best quality)",
            familyDescription = "Apache 2.0 - LiteRT-LM - largest download - needs 8GB+ RAM",
            fileName = "gemma4-e4b.litertlm",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b.litertlm",
            ),
            huggingFaceRepos = listOf(
                "litert-community/gemma-4-E4B-it-litert-lm",
                "litert-community/gemma-4-E4B-it",
            ),
            preferredHuggingFaceFiles = listOf(
                "gemma-4-E4B-it.litertlm",
                "gemma-4-E4B-it-web.task",
            ),
            taskPreferencePenalty = 8,
            litertLmPreferencePenalty = 0,
            expectedSizeBytes = 5_000_000_000L,
            sha256 = null,
        ),
        GEMMA3_1B_TASK(
            key = "gemma3_1b_int4_task",
            displayName = "Gemma 3 1B (INT4, fast fallback)",
            familyDescription = "Apache 2.0 - 4-bit - about 600 MB - fastest fallback",
            fileName = "gemma3-1b-it-int4.task",
            urls = listOf(
                "https://huggingface.co/litert-community/gemma-3-1b-it/resolve/main/gemma-3-1b-it-int4.task",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma3-1b-it-int4.task",
            ),
            huggingFaceRepos = listOf("litert-community/gemma-3-1b-it"),
            preferredHuggingFaceFiles = listOf("gemma-3-1b-it-int4.task"),
            taskPreferencePenalty = 0,
            litertLmPreferencePenalty = 20,
            expectedSizeBytes = 600_000_000L,
            sha256 = null,
        ),
        GEMMA2_2B_TASK(
            key = "gemma2_2b_int4_task",
            displayName = "Gemma 2 2B (INT4, legacy gated)",
            familyDescription = "Gemma TOU - 4-bit - about 1.35 GB - usually gated, sideload preferred",
            fileName = "gemma2-2b-it-int4.task",
            urls = listOf(
                "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q4_ekv1280.task",
                "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float16/1/gemma-2b-it-cpu-int4.bin",
                "https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma2-2b-it-int4.task",
            ),
            huggingFaceRepos = listOf("litert-community/Gemma2-2B-IT"),
            preferredHuggingFaceFiles = listOf(
                "Gemma2-2B-IT_multi-prefill-seq_q4_ekv1280.task",
            ),
            taskPreferencePenalty = 0,
            litertLmPreferencePenalty = 20,
            expectedSizeBytes = 1_350_000_000L,
            sha256 = null,
        );

        companion object {
            fun fromKey(k: String): ModelVariant =
                entries.firstOrNull { it.key == k } ?: GEMMA4_E2B_INT4_LITERTLM
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

    companion object {
        private const val MIN_VALID_SIZE_BYTES = 100L * 1024 * 1024
    }
}
