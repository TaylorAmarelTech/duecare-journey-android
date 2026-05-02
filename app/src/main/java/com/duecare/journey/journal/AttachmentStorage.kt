package com.duecare.journey.journal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.7: copies user-picked attachments (typically receipt photos +
 * contract scans) into the app's private internal storage so they
 * survive a gallery deletion and can be reliably re-shared with the
 * NGO intake document.
 *
 * Storage location: `context.filesDir/attachments/<uuid>.<ext>`. This
 * directory is:
 *   - Sandboxed per-app per Android's standard storage model
 *   - Excluded from backup (`allowBackup="false"` in the manifest)
 *   - Wiped on app uninstall
 *   - Wiped on Settings → Panic wipe
 *
 * v0.8 will layer Tink AES-GCM streaming encryption on top so even a
 * rooted device or filesystem dump leaks nothing without the keystore-
 * sealed master key. For v0.7 the OS sandbox is the active boundary.
 */
@Singleton
class AttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private fun dir(): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    /** Copy [sourceUri] into private storage. Returns the relative
     *  path (e.g. `attachments/abc.jpg`) suitable for
     *  [JournalEntry.attachmentPath], or null on failure. */
    fun copyIn(sourceUri: Uri, contentResolver: ContentResolver): String? {
        return try {
            val ext = guessExtension(sourceUri, contentResolver)
            val name = "${UUID.randomUUID()}.$ext"
            val target = File(dir(), name)
            contentResolver.openInputStream(sourceUri).use { input ->
                if (input == null) return null
                target.outputStream().use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            "attachments/$name"
        } catch (e: Throwable) {
            Log.w(TAG, "copyIn failed: $e")
            null
        }
    }

    fun resolve(relativePath: String): File =
        File(context.filesDir, relativePath)

    fun delete(relativePath: String): Boolean {
        val f = resolve(relativePath)
        return if (f.exists()) f.delete() else true
    }

    fun deleteAll(): Int {
        val d = dir()
        if (!d.exists()) return 0
        val children = d.listFiles() ?: return 0
        var deleted = 0
        children.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    private fun guessExtension(uri: Uri, cr: ContentResolver): String {
        val mime = cr.getType(uri)
        return when {
            mime == null -> "bin"
            mime.startsWith("image/jpeg") -> "jpg"
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/heic") -> "heic"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("application/pdf") -> "pdf"
            mime.startsWith("text/") -> "txt"
            else -> mime.substringAfterLast('/').take(8).ifBlank { "bin" }
        }
    }

    private companion object {
        const val TAG = "AttachmentStorage"
    }
}
