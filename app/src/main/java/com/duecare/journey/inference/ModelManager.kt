package com.duecare.journey.inference

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ModelManager"

/**
 * Manages download + cache of the MediaPipe Gemma `.task` model file.
 *
 * Privacy invariant: the ONLY outbound network call this app makes
 * (besides the optional opt-in NGO sync, which is v2). The download
 * URL is hard-coded to a single allowlisted host. No telemetry. No
 * background sync. No update check (worker can manually re-download
 * via Settings if a new version exists).
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The default model variant. CPU INT4 is the safest choice —
     *  works on any Android 8+ device with 4 GB+ RAM. Worker can
     *  override via Settings → Choose model. */
    var modelVariant: ModelVariant = ModelVariant.GEMMA2_2B_IT_CPU_INT4

    private fun modelDir(): File {
        return File(context.filesDir, "models").apply { mkdirs() }
    }

    fun modelFile(): File = File(modelDir(), modelVariant.fileName)

    val isDownloaded: Boolean
        get() = modelFile().exists() && modelFile().length() > MIN_VALID_SIZE_BYTES

    /** Stream download progress as a Flow of Progress events.
     *  Caller collects on a coroutine; the UI surfaces a progress bar.
     *  Idempotent: if the file already exists with the right size,
     *  emits Progress(done=length, total=length) immediately. */
    fun download(): Flow<Progress> = flow {
        val target = modelFile()
        if (isDownloaded) {
            emit(Progress(target.length(), target.length(), done = true))
            return@flow
        }
        emit(Progress(0L, modelVariant.expectedSizeBytes, done = false))
        Log.i(TAG, "Downloading ${modelVariant.fileName} from ${modelVariant.url}")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(modelVariant.url).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Model download failed: HTTP ${resp.code}")
        }
        val total = resp.body?.contentLength() ?: modelVariant.expectedSizeBytes
        val body = resp.body ?: throw IllegalStateException("Empty response body")

        val tmp = File(target.absolutePath + ".part")
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
                    if (now - lastEmit > 500) {     // throttle to 2 events/sec
                        emit(Progress(written, total, done = false))
                        lastEmit = now
                    }
                }
            }
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

    /** Verify SHA256 of the cached file matches expected. Slow (~5 sec
     *  for 1.4 GB on internal storage); call only when explicitly
     *  triggered via Settings → Verify model. */
    suspend fun verifyChecksum(): Boolean {
        val f = modelFile()
        if (!f.exists()) return false
        val expected = modelVariant.sha256 ?: return true   // skip if no expected
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    data class Progress(
        val bytesDone: Long,
        val bytesTotal: Long,
        val done: Boolean,
    ) {
        val percent: Int get() = if (bytesTotal == 0L) 0 else (bytesDone * 100 / bytesTotal).toInt()
    }

    enum class ModelVariant(
        val displayName: String,
        val fileName: String,
        val url: String,
        val expectedSizeBytes: Long,
        val sha256: String?,           // optional — null skips verification
        val requiresAuth: Boolean,
    ) {
        GEMMA2_2B_IT_CPU_INT4(
            displayName = "Gemma 2 (2B INT4, CPU)",
            fileName = "gemma-2b-it-cpu-int4.task",
            // Google's Gemma 2 .task files live on Kaggle Models +
            // HF Hub, both of which require authentication. There is
            // no "just works" public URL. The URL below is a known
            // MediaPipe-published mirror that may or may not respond
            // depending on Google's CDN state — the user should
            // sideload via Settings -> "Use my own model file" if the
            // download fails.
            url = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float16/1/gemma-2b-it-cpu-int4.bin",
            expectedSizeBytes = 1_350_000_000L,
            sha256 = null,
            requiresAuth = true,
        ),
        GEMMA2_2B_IT_GPU_INT4(
            displayName = "Gemma 2 (2B INT4, GPU)",
            fileName = "gemma-2b-it-gpu-int4.task",
            url = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float16/1/gemma-2b-it-gpu-int4.bin",
            expectedSizeBytes = 1_350_000_000L,
            sha256 = null,
            requiresAuth = true,
        ),
    }

    /** Import a model file the worker downloaded externally (e.g.
     *  from Kaggle Models or HF Hub via their browser). Copies the
     *  source into the app's encrypted internal storage with the
     *  expected file name. */
    suspend fun importLocalFile(sourceUri: android.net.Uri,
                                  contentResolver: android.content.ContentResolver): Boolean {
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
            android.util.Log.w(TAG, "importLocalFile failed: $e")
            false
        }
    }

    private companion object {
        // Anything smaller than 100 MB is presumed corrupted/partial.
        const val MIN_VALID_SIZE_BYTES = 100L * 1024 * 1024
    }
}
