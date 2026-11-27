package iad1tya.echo.music.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import iad1tya.echo.music.db.MusicDatabaseEntryPoint
import iad1tya.echo.music.utils.CrashHandler
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.viewmodels.BackupRestoreViewModel
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Registry row 908: «Copia automática» on/off (off by default). New keys: never rename them. */
val AutoBackupEnabledKey = booleanPreferencesKey("auto_backup_enabled")

/** [AutoBackupFrequency] by name; weekly when unset. */
val AutoBackupFrequencyKey = stringPreferencesKey("auto_backup_frequency")

/** The SAF tree the user picked (a persisted read/write grant). */
val AutoBackupFolderKey = stringPreferencesKey("auto_backup_folder")

/** When the last automatic copy was written, and when the last one failed (epoch ms; 0 = never). */
val AutoBackupLastOkKey = longPreferencesKey("auto_backup_last_ok")
val AutoBackupLastFailedKey = longPreferencesKey("auto_backup_last_failed")

/**
 * Registry row 908: writes the SAME ZIP as Ajustes ▸ Copia de seguridad ▸ «Copia de seguridad local»
 * ([BackupRestoreViewModel.LibraryBackupZip]: song.db + the EQ/appearance whitelist, never the session DataStore nor license/,
 * see [BackupExclusions]) into the folder the user chose, and keeps the newest [AutoBackupPolicy.KEEP]. Restoring one is the
 * existing «Importar desde archivo local» (BackupRestoreViewModel.restore, unchanged).
 *
 * Rule 7: WorkManager only, never a loop — once a day or a week, with the battery and storage not low. One attempt per run: a
 * folder that went away is reported on the settings row, not retried in the background.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = ctx.dataStore.data.first()
        if (prefs[AutoBackupEnabledKey] != true) return@withContext Result.success()
        // A restore closes the database and restarts the app (row 19): never snapshot in that window.
        if (CrashHandler.isRestoring) return@withContext Result.success()
        val dir = prefs[AutoBackupFolderKey]?.let { runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it)) }.getOrNull() }
        if (dir == null || !dir.isDirectory || !dir.canWrite()) {
            Timber.tag(TAG).w("folder not writable; copy skipped")
            markFailed(ctx)
            return@withContext Result.success()
        }
        val file = runCatching { dir.createFile("application/octet-stream", AutoBackupPolicy.fileName(LocalDateTime.now())) }.getOrNull()
        if (file == null) {
            Timber.tag(TAG).w("could not create the copy file")
            markFailed(ctx)
            return@withContext Result.success()
        }
        val written = runCatching {
            BackupRestoreViewModel.LibraryBackupZip.write(ctx, MusicDatabaseEntryPoint.get(ctx), file.uri)
        }
        if (written.isFailure) {
            Timber.tag(TAG).w("copy failed: %s", written.exceptionOrNull()?.javaClass?.simpleName)
            runCatching { file.delete() }
            markFailed(ctx)
            return@withContext Result.success()
        }
        val removed = runCatching {
            val files = dir.listFiles().filter { it.isFile }
            val gone = AutoBackupPolicy.toDelete(files.mapNotNull { it.name }).toSet()
            files.filter { it.name in gone }.count { it.delete() }
        }.getOrDefault(0)
        ctx.dataStore.edit { it[AutoBackupLastOkKey] = System.currentTimeMillis() }
        Timber.tag(TAG).i("copy written, %d old copies removed", removed)
        Result.success()
    }

    private suspend fun markFailed(ctx: Context) {
        ctx.dataStore.edit { it[AutoBackupLastFailedKey] = System.currentTimeMillis() }
    }

    companion object {
        private const val TAG = "AutoBackup"
        const val WORK_NAME = "auto_backup"

        /** On = (re)schedule at [frequency] (UPDATE keeps the cadence in step with the setting); off = cancel. */
        fun schedule(context: Context, enabled: Boolean, frequency: AutoBackupFrequency) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
