package iad1tya.echo.music.echomusic.updater

import android.content.Context
import android.os.Environment
import iad1tya.echo.music.echomusic.updater.downloadmanager.DownloadNotificationManager
import java.io.File

/**
 * Housekeeping for the update APKs on disk.
 *
 * "Borrar actualizaciones descargadas" used to be the only way to get a new update to install: the
 * updater kept one fixed `echomusic.apk`, so deleting it was what forced a real download. Files are
 * versioned now and every install is checked against the archive itself, so this is what its name
 * says — freeing space — and nothing depends on the user pressing it.
 */

fun getDownloadedApksDir(context: Context): File {
    return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "echo_updates")
}

private fun ourFiles(context: Context): List<File> {
    val dir = getDownloadedApksDir(context)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()?.filter { it.isFile && UpdateApkFiles.isOwnUpdateFile(it.name) } ?: emptyList()
}

/**
 * How many finished update APKs this app is holding — the private copy plus the copies it placed in
 * the user's Downloads folder, which is where they actually piled up (one per release, ~100 MB each).
 */
fun getDownloadedApkCount(context: Context): Int =
    ourFiles(context).count { it.name.endsWith(UpdateApkFiles.APK_SUFFIX, ignoreCase = true) } +
        runCatching { PublicDownloads.countOurApks(context) }.getOrDefault(0)

/** Bytes the update files hold on the phone (private directory, partial downloads included, plus public copies). */
fun getDownloadedApkBytes(context: Context): Long =
    ourFiles(context).sumOf { it.length() } + runCatching { PublicDownloads.ourApksBytes(context) }.getOrDefault(0L)

/** What a manual clean did. [blocked] = rows in Downloads this app does not own; [consent] asks the user to allow them. */
class ClearResult(
    val removedBytes: Long,
    val allDeleted: Boolean,
    val blocked: List<android.net.Uri>,
    val consent: android.content.IntentSender?,
)

/**
 * Deletes every update file this app wrote: the private copies AND the copies it placed in the user's
 * Downloads folder. Only names this app itself produces are touched. A copy in Downloads that another app owns (the
 * browser that fetched it, an older install) cannot be removed silently: it comes back in [ClearResult.blocked] with the
 * system's own consent request, instead of being reported as deleted.
 */
fun clearDownloadedApksDetailed(context: Context): ClearResult {
    var allDeleted = true
    var freed = 0L
    ourFiles(context).forEach { file ->
        val size = file.length()
        if (file.delete()) freed += size else allDeleted = false
    }
    val report = runCatching { PublicDownloads.deleteOurApksReport(context, keepNames = emptySet()) }.getOrNull()
    if (report != null) freed += report.freedBytes
    // The "tap to install" notification would otherwise point at a file that is gone.
    runCatching { DownloadNotificationManager.cancelNotification(context) }
    val blocked = report?.blocked.orEmpty()
    return ClearResult(
        removedBytes = freed,
        allDeleted = allDeleted && blocked.isEmpty(),
        blocked = blocked,
        consent = PublicDownloads.deleteRequest(context, blocked),
    )
}

/** Compatibility wrapper: true when nothing was left behind. */
fun clearDownloadedApks(context: Context): Boolean = clearDownloadedApksDetailed(context).allDeleted

/**
 * Frees what the update installer has already used: every update file (private and in Downloads) whose version is the
 * one running now or an older one. A newer file — the pending update — and its resumable partial stay. Runs at startup;
 * nothing depends on the user pressing the button (owner 2026-10-19: «que de verdad pueda eliminar las actualizaciones
 * descargadas para no llenar el espacio en el cel»).
 */
fun cleanInstalledUpdates(context: Context): Long {
    val current = iad1tya.echo.music.BuildConfig.VERSION_NAME
    var freed = 0L
    val dir = getDownloadedApksDir(context)
    val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
    UpdateApkFiles.installedUpdateNames(files.map { it.name }, current).toSet().let { doomed ->
        files.filter { it.name in doomed }.forEach { file ->
            val size = file.length()
            if (file.delete()) freed += size
        }
    }
    val report = runCatching {
        PublicDownloads.deleteOurApksReport(context, keepNames = emptySet()) { name ->
            val version = UpdateApkFiles.versionOfFileName(name)
            version != null && !version.startsWith("nightly", ignoreCase = true) &&
                !UpdateApkFiles.isNewerRelease(version, current)
        }
    }.getOrNull()
    if (report != null) freed += report.freedBytes
    return freed
}

/**
 * Drops update files older than a day. Kept for the space it frees; it is no longer load-bearing for
 * correctness — a leftover APK from another release can no longer be installed by mistake.
 */
fun autoClearOldApks(context: Context) {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    ourFiles(context)
        .filter { it.lastModified() < oneDayAgo }
        .forEach { it.delete() }
}
