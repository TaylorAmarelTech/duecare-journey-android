package com.duecare.journey.inference

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ModelManager"

private val Context.modelPrefs by preferencesDataStore("duecare_model_settings")

/**
 * Manages download + cache of the MediaPipe / LiteRT-LM model file.
 *
 * v0.5 redesign per docs/model_distribution.md:
 *   - Default model: Gemma 4 E2B from `litert-community/gemma-4-E2B-it-litert-lm`
 *     (Apache 2.0, ungated, anonymous HF Hub download — no Gemma TOU
 *     plumbing, no Kaggle login).
 *   - Custom URL override: worker can paste any direct download URL
 *     in Settings. Useful when our default URL becomes stale, when
 *     a fine-tuned variant is published elsewhere, or when an NGO
 *     hosts a private mirror.
 *   - SHA-256 verification: optional, configurable. MediaPipe
 *     surfaces "Invalid Flatbuffer" with no structured error code on
 *     corrupted/partial files; SHA verify catches this before
 *     LlmInference.createFromOptions() blows up.
 *   - Wi-Fi awareness: refuses to start a 1.4 GB download on metered
 *     networks unless the worker explicitly opts in.
 *
 * Privacy invariant: the ONLY outbound network call this app makes
 * (besides the optional opt-in NGO sync, which is v2). No telemetry.
 * No background sync. No update check.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The default variant. Gemma 4 E2B on the litert-community HF
     *  Hub repo: ungated, Apache 2.0, anonymous direct download. */
    var modelVariant: ModelVariant = ModelVariant.GEMMA4_E2B_LITERTLM

    private val customUrlKey = stringPreferencesKey("custom_model_url")
    private val customShaKey = stringPreferencesKey("custom_model_sha256")

    /** Worker-set custom URL. If non-empty, overrides [modelVariant.url]. */
    val customUrl: Flow<String> = context.modelPrefs.data
        .map { it[customUrlKey].orEmpty() }

    /** Worker-set custom SHA-256 (hex). Used to verify a downloaded
     *  file matches the expected. Empty disables verification. */
    val customSha256: Flow<String> = context.modelPrefs.data
        .map { it[customShaKey].orEmpty() }

    suspend fun setCustomUrl(url: String, sha256: String = "") {
        context.modelPrefs.edit { prefs ->
            prefs[customUrlKey] = url.trim()
            prefs[customShaKey] = sha256.trim()
        }
    }

    suspend fun resolvedUrl(): String {
        val custom = customUrl.first()
        return if (custom.isNotBlank()) custom else modelVariant.url
    }

    private suspend fun resolvedSha256(): String {
        val custom = customSha256.first()
        return if (custom.isNotBlank()) custom else (modelVariant.sha256 ?: "")
    }

    private fun modelDir(): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(): File = File(modelDir(), modelVariant.fileName)

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
     *  Caller collects on a coroutine; the UI surfaces a progress bar.
     *  Idempotent: if the file already exists with the right size,
     *  emits Progress(done=length, total=length) immediately. The
     *  flow throws on network errors / non-200 responses; callers
     *  should wrap in try/catch.
     *
     *  `requireUnmetered` (default true) refuses to start a download
     *  over cellular. Set false to allow cellular downloads (UI
     *  already shows the data cost disclosure).
     *
     *  After the file is fully downloaded, runs SHA-256 verification
     *  if a hash is configured. On verify failure, deletes the bad
     *  file and throws. */
    fun download(requireUnmetered: Boolean = true): Flow<Progress> = flow {
        val target = modelFile()
        if (isDownloaded) {
            emit(Progress(target.length(), target.length(), done = true))
            return@flow
        }
        if (requireUnmetered && !isOnUnmeteredNetwork()) {
            throw IllegalStateException(
                "Refusing to start ${modelVariant.expectedSizeBytes / 1024 / 1024} MB " +
                    "download on metered (cellular) network. Switch to Wi-Fi, or " +
                    "tap 'Start anyway' to confirm cellular use."
            )
        }
        val url = resolvedUrl()
        val expectedSha = resolvedSha256()
        emit(Progress(0L, modelVariant.expectedSizeBytes, done = false))
        Log.i(TAG, "Downloading ${modelVariant.fileName} from $url " +
                "(expected ~${modelVariant.expectedSizeBytes / 1024 / 1024} MB, " +
                "verifying sha256: ${expectedSha.isNotEmpty()})")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "DuecareJourney/0.5 (Android)")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException(
                "Model download failed: HTTP ${resp.code} from $url"
            )
        }
        val total = resp.body?.contentLength()?.takeIf { it > 0 }
            ?: modelVariant.expectedSizeBytes
        val body = resp.body ?: throw IllegalStateException("Empty response body")

        val tmp = File(target.absolutePath + ".part")
        // Resume if a partial exists (simple resume: skip server-side range,
        // restart from 0; full Range-resume is a v0.6 enhancement).
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
                        emit(Progress(written, total, done = false))
                        lastEmit = now
                    }
                }
            }
        }

        // Verify before atomic rename — corrupted file never reaches
        // the canonical path
        if (expectedSha.isNotEmpty()) {
            emit(Progress(total, total, done = false, verifying = true))
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

        if (!tmp.renameTo(target)) {
            throw IllegalStateException("Failed to move downloaded file into place")
        }
        emit(Progress(total, total, done = true))
        Log.i(TAG, "Model download complete: ${target.length()} bytes")
    }.flowOn(Dispatchers.IO)

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

    enum class ModelVariant(
        val displayName: String,
        val fileName: String,
        val url: String,
        val expectedSizeBytes: Long,
        val sha256: String?,
    ) {
        /** Gemma 4 E2B INT8 LiteRT-LM bundle. The recommended default —
         *  Apache 2.0, ungated on Hugging Face, anonymous direct
         *  download. The actual filename follows litert-community's
         *  naming convention; if HF changes it, override via Settings
         *  -> Custom URL. */
        GEMMA4_E2B_LITERTLM(
            displayName = "Gemma 4 (E2B LiteRT-LM)",
            fileName = "gemma-4-e2b-it-litert-lm.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
                "Gemma4-E2B-it_multi-prefill-seq_q8_ekv1280.litertlm",
            expectedSizeBytes = 1_500_000_000L,
            sha256 = null,
        ),
        /** Legacy Gemma 2 path. Kept for users who already pulled a
         *  .task file. Gated on Kaggle / HF — auto-download usually
         *  fails; sideload via Settings → Use my own model file. */
        GEMMA2_2B_TASK(
            displayName = "Gemma 2 (2B INT4) — gated, sideload preferred",
            fileName = "gemma-2b-it-cpu-int4.task",
            url = "https://storage.googleapis.com/mediapipe-models/llm_inference/" +
                "gemma-2b-it-cpu-int4/float16/1/gemma-2b-it-cpu-int4.bin",
            expectedSizeBytes = 1_350_000_000L,
            sha256 = null,
        ),
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
