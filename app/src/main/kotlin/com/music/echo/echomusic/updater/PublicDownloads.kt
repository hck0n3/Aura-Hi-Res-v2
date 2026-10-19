package iad1tya.echo.music.echomusic.updater

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a finished update APK into the user's public **Downloads** folder so it's easy to find
 * (and re-install manually) from any file manager — not buried in the app-private
 * `Android/data/.../files/Download` directory used for the in-app install.
 *
 * On API 29+ this goes through MediaStore (no storage permission needed, survives scoped storage).
 * On older devices it falls back to the public Downloads dir. Best-effort: failures are swallowed so
 * a copy problem never breaks the actual update install.
 */
object PublicDownloads {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /** Copies [source] into public Downloads as [displayName]. Returns true on success. */
    fun saveApk(context: Context, source: File, displayName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, displayName)
        } else {
            saveToPublicDir(source, displayName)
        }
    } catch (_: Exception) {
        false
    }

    private fun saveViaMediaStore(context: Context, source: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_APK)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: return false
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    /**
     * Removes the update APKs THIS app copied here, except [keepNames]. Returns how many went.
     *
     * The public Downloads folder holds the user's own files, so the filter is deliberately narrow
     * ([UpdateApkFiles.publicApksToDelete]): only `Aura-Hi-Res-v2-<version>.apk` names this app
     * could have written, never `-NOSUB` builds, never anything else. On API 29+ MediaStore refuses to
     * delete rows another app owns, which is a second, system-enforced guard; that failure is caught
     * and skipped.
     *
     * Without this, every update left one ~100 MB APK behind forever — and a re-download of the same
     * version added a `… (1).apk` duplicate next to it.
     */
    fun deleteOurApks(context: Context, keepNames: Set<String>): Int =
        deleteOurApksReport(context, keepNames).removed

    /** What a delete pass did: how many went, how many bytes that freed, and the rows the system would not let us delete. */
    class DeleteReport(val removed: Int, val freedBytes: Long, val blocked: List<android.net.Uri>)

    /**
     * [deleteOurApks] with the full outcome. A row another app owns (the browser that downloaded it, an older install of
     * this app) throws on delete: it used to be skipped silently, so the screen kept counting an APK it could never remove
     * and said "deleted" anyway. Those rows come back in [DeleteReport.blocked] so the caller can ask the system's own
     * consent dialog ([deleteRequest]). [onlyIf] narrows the pass (e.g. "only versions already installed").
     */
    fun deleteOurApksReport(
        context: Context,
        keepNames: Set<String>,
        onlyIf: (String) -> Boolean = { true },
    ): DeleteReport = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteViaMediaStore(context, keepNames, onlyIf)
        } else {
            DeleteReport(deleteFromPublicDir(keepNames, onlyIf), 0L, emptyList())
        }
    } catch (_: Exception) {
        DeleteReport(0, 0L, emptyList())
    }

    /**
     * The system's own "Allow Aura to delete these files?" request for rows this app does not own (API 30+). The caller
     * launches the returned sender and re-counts when it comes back; on API 29 there is no batch request, so null.
     */
    fun deleteRequest(context: Context, blocked: List<android.net.Uri>): android.content.IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && blocked.isNotEmpty()) {
            runCatching { MediaStore.createDeleteRequest(context.contentResolver, blocked).intentSender }.getOrNull()
        } else {
            null
        }

    /** Total bytes of our update APKs sitting in the user's Downloads folder. */
    fun ourApksBytes(context: Context): Long = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rows = queryOurApks(context)
            val doomed = UpdateApkFiles.publicApksToDelete(rows.map { it.second }, emptySet()).toSet()
            rows.filter { it.second in doomed }.sumOf { it.third }
        } else {
            0L
        }
    } catch (_: Exception) {
        0L
    }

    /** Display names of update APKs currently in the user's Downloads folder. */
    @Suppress("DEPRECATION")
    private fun ourApkNames(context: Context): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryOurApks(context).map { it.second }
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        }

    /** How many of our own update APKs are sitting in the user's Downloads folder right now. */
    fun countOurApks(context: Context): Int = try {
        UpdateApkFiles.publicApksToDelete(ourApkNames(context), keepNames = emptySet()).size
    } catch (_: Exception) {
        0
    }

    /** Is this exact APK already in Downloads? Avoids copying ~100 MB that is already there. */
    fun hasApk(context: Context, displayName: String): Boolean = try {
        ourApkNames(context).any { it == displayName }
    } catch (_: Exception) {
        false
    }

    /** MediaStore rows in Downloads whose display name starts with our APK prefix. */
    private fun queryOurApks(context: Context): List<Triple<Long, String, Long>> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
        val rows = ArrayList<Triple<Long, String, Long>>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("${UpdateApkFiles.FILE_PREFIX}%"),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (cursor.moveToNext()) {
                rows.add(Triple(cursor.getLong(idColumn), cursor.getString(nameColumn), cursor.getLong(sizeColumn)))
            }
        }
        return rows
    }

    private fun deleteViaMediaStore(context: Context, keepNames: Set<String>, onlyIf: (String) -> Boolean): DeleteReport {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val candidates = queryOurApks(context)
        val doomed = UpdateApkFiles.publicApksToDelete(candidates.map { it.second }, keepNames).filter(onlyIf).toSet()
        var removed = 0
        var freed = 0L
        val blocked = ArrayList<android.net.Uri>()
        candidates.filter { it.second in doomed }.forEach { (id, _, size) ->
            val uri = android.content.ContentUris.withAppendedId(collection, id)
            // A row another app owns throws SecurityException: remember it, so the caller can ask the user for consent.
            try {
                if (resolver.delete(uri, null, null) > 0) {
                    removed++
                    freed += size
                }
            } catch (_: SecurityException) {
                blocked.add(uri)
            } catch (_: Exception) {
                // Anything else: leave the row alone.
            }
        }
        return DeleteReport(removed, freed, blocked)
    }

    @Suppress("DEPRECATION")
    private fun deleteFromPublicDir(keepNames: Set<String>, onlyIf: (String) -> Boolean): Int {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.isDirectory) return 0
        // Pre-Q there is no MediaStore ownership to lean on, so the name filter is the only guard.
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: return 0
        var removed = 0
        UpdateApkFiles.publicApksToDelete(names, keepNames).filter(onlyIf).forEach { name ->
            if (runCatching { File(dir, name).delete() }.getOrDefault(false)) removed++
        }
        return removed
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDir(source: File, displayName: String): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, displayName)
        source.inputStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest.exists()
    }
}
