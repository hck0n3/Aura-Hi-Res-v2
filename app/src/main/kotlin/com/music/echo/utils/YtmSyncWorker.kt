package iad1tya.echo.music.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.music.innertube.utils.parseCookieString
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.InnerTubeCookieKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Runs a YouTube Music sync in the background via WorkManager so it **survives the app being closed**
 * and **retries until it completes** (network-constrained, with backoff). The syncs are additive
 * (insert/upsert), so a retry after an interruption simply continues filling the library — nothing is
 * lost or duplicated destructively. This is what makes "Sincronizar todo" reliable for big libraries.
 */
class YtmSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface YtmSyncEntryPoint {
        fun syncUtils(): SyncUtils
        fun libraryUploadSync(): LibraryUploadSync

        /** Para la descarga automática por lista — ver el final de [doWork]. */
        fun database(): iad1tya.echo.music.db.MusicDatabase
        fun downloadUtil(): iad1tya.echo.music.playback.DownloadUtil
    }

    /**
     * Notificación del trabajo de LARGA DURACIÓN. Ver [promoteToForeground] para el porqué.
     * Es silenciosa (IMPORTANCIA_LOW, sin badge) y su único trabajo es doble: quitarle al sistema el
     * límite de ejecución, y que él pueda VER que la sincronización sigue en marcha — media queja era
     * justamente esa, que no había forma de saberlo.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService<NotificationManager>()
            if (nm?.getNotificationChannel(SYNC_CHANNEL_ID) == null) {
                nm?.createNotificationChannel(
                    NotificationChannel(
                        SYNC_CHANNEL_ID,
                        ctx.getString(R.string.sync_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
        val notification = NotificationCompat.Builder(ctx, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentTitle(ctx.getString(R.string.sync_notification_title))
            .setContentText(ctx.getString(R.string.sync_notification_text))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SYNC_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SYNC_NOTIFICATION_ID, notification)
        }
    }

    /**
     * EL ARREGLO DE *"le doy sincronizar y la tarea no continúa hasta terminar; vuelvo al apartado y
     * tengo que volverle a dar sincronizar ahora"* (dueño, 2026-09-17, y lo llamó imprescindible).
     *
     * Un `CoroutineWorker` normal tiene un **límite de ejecución de ~10 minutos**: pasado ese tiempo el
     * sistema lo para donde vaya. Una biblioteca grande (canciones con “Me gusta”, álbumes, artistas,
     * playlists guardadas y la biblioteca entera, cada una con su paginación) no cabe ahí, y
     * `performFullSyncSuspend` **empieza desde arriba en cada intento** — así que cada reintento volvía
     * a gastar los mismos diez minutos en la primera parte y nunca llegaba al final. Exactamente el
     * bucle que él describe.
     *
     * Un trabajo de PRIMER PLANO no tiene ese límite. El permiso `FOREGROUND_SERVICE_DATA_SYNC` y el
     * `SystemForegroundService` con `foregroundServiceType="dataSync"` ya estaban en el manifest (los
     * puso la descarga de actualizaciones), así que no hace falta nada nuevo.
     *
     * Si la promoción falla **no se aborta**: Android 12+ prohibe arrancar un servicio en primer plano
     * desde segundo plano en algunos casos, y en esos la sincronización debe seguir como trabajo normal
     * — peor (con el límite de vuelta), pero funcionando. Rendirse aquí cambiaría “a veces no termina”
     * por “a veces no empieza”, que es peor.
     */
    private suspend fun promoteToForeground() {
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Timber.tag(TAG).w("No se pudo promover la sincronización a primer plano: ${it.javaClass.simpleName}") }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val cookie = ctx.dataStore.get(InnerTubeCookieKey, "")
            if (cookie.isBlank() || "SAPISID" !in parseCookieString(cookie)) {
                // Not signed in → nothing to sync (don't retry forever).
                return@withContext Result.success()
            }
            // Solo DESPUÉS de saber que hay sesión: una notificación de “sincronizando” para quien no
            // ha iniciado sesión sería ruido por una tarea que no va a hacer nada.
            promoteToForeground()
            val entryPoint = EntryPointAccessors
                .fromApplication(ctx, YtmSyncEntryPoint::class.java)
            val sync = entryPoint.syncUtils()

            val type = inputData.getString(KEY_TYPE)
            val startedAt = System.currentTimeMillis()
            Timber.tag(TAG).i("SYNC_START type=$type attempt=$runAttemptCount")
            when (type) {
                TYPE_LIKED_SONGS -> sync.syncLikedSongsSuspend()
                TYPE_LIKED_ALBUMS -> sync.syncLikedAlbumsSuspend()
                TYPE_ARTISTS -> sync.syncArtistsSubscriptionsSuspend()
                TYPE_PLAYLISTS -> sync.syncSavedPlaylistsSuspend()
                TYPE_LIBRARY -> sync.syncLibrarySongsSuspend()
                TYPE_UPLOADS -> {
                    sync.syncUploadedSongsSuspend()
                    sync.syncUploadedAlbumsSuspend()
                }
                TYPE_UPLOAD_LIBRARY -> runUploadPass(ctx, entryPoint.libraryUploadSync())
                else -> sync.performFullSyncSuspend()
            }
            // TERMINÓ de verdad. Sin esta línea no había forma de distinguir en un log compartido una
            // sincronización completa de una que el sistema cortó a los diez minutos — que es justo la
            // diferencia que había que demostrar.
            Timber.tag(TAG).i("SYNC_DONE type=$type tookMs=${System.currentTimeMillis() - startedAt}")
            // 🔴 DESCARGA AUTOMÁTICA POR LISTA (punto 3 del dueño, 2026-09-17). Una sincronización
            // es justo como entran las canciones que él añadió desde OTRO aparato, así que es uno de los
            // tres momentos en que la respuesta a "qué debería estar descargado" puede haber cambiado.
            // El reconciliador es idempotente y, si ninguna lista tiene el interruptor puesto, cuesta
            // una sola consulta. Ver [iad1tya.echo.music.playback.PlaylistAutoDownload].
            runCatching {
                iad1tya.echo.music.playback.PlaylistAutoDownload.reconcile(
                    context = ctx,
                    database = entryPoint.database(),
                    stateById = entryPoint.downloadUtil().downloads.value.mapValues { it.value.state },
                )
            }.onFailure { Timber.tag(TAG).w(it, "auto-download reconcile failed") }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Interrupted / network error / app closed → reschedule and continue (additive, safe).
            Timber.tag(TAG).e(e, "YTM sync failed; will retry")
            Result.retry()
        }
    }

    /**
     * One bounded upload pass, plus — only if the pass ran out of its request budget with work still
     * pending — ONE delayed continuation. This is what makes a whole-library backfill finish without
     * ever becoming a burst: each pass is capped at [LibraryUploadSync.MAX_REQUESTS_PER_RUN] writes,
     * and passes are spaced [UPLOAD_CONTINUATION_DELAY_MIN] minutes apart. The chain stops when the
     * library is fully synced, when nothing moved, or after [MAX_UPLOAD_CONTINUATIONS] hops — it can
     * never turn into an unbounded background loop, and nothing here runs at app start.
     */
    private suspend fun runUploadPass(context: Context, uploader: LibraryUploadSync) {
        uploader.restorePersistedProgress()
        val done = uploader.runUploadPass()
        if (done) return

        val hop = inputData.getInt(KEY_UPLOAD_HOP, 0)
        val state = uploader.progress.value
        // Only continue when there really is more to do AND the pass was cut short by its own budget.
        // A pass that stopped because the user is offline/signed out/opted out just waits for the next
        // explicit request instead of burning wakeups.
        if (!state.moreWorkPending || state.stoppedReason != null) return
        if (hop >= MAX_UPLOAD_CONTINUATIONS) {
            Timber.tag(TAG).i("Upload continuation limit reached; the rest waits for the next sync")
            return
        }
        enqueueUploadContinuation(context, hop + 1)
    }

    companion object {
        private const val TAG = "YtmSyncWorker"

        /** Canal silencioso del trabajo de primer plano. Ver [getForegroundInfo]. */
        private const val SYNC_CHANNEL_ID = "library_sync"
        private const val SYNC_NOTIFICATION_ID = 0x5C10
        const val KEY_TYPE = "type"
        const val TYPE_ALL = "all"
        const val TYPE_LIKED_SONGS = "liked_songs"
        const val TYPE_LIKED_ALBUMS = "liked_albums"
        const val TYPE_ARTISTS = "artists"
        const val TYPE_PLAYLISTS = "playlists"
        const val TYPE_LIBRARY = "library"
        const val TYPE_UPLOADS = "uploads"

        /** Push the local library UP to the YouTube account (playlists, follows, likes, albums). */
        const val TYPE_UPLOAD_LIBRARY = "upload_library"

        private const val KEY_UPLOAD_HOP = "upload_hop"

        /** Upper bound on chained upload passes. 20 hops x 400 writes covers a very large library. */
        private const val MAX_UPLOAD_CONTINUATIONS = 20

        /** Spacing between chained passes — keeps the whole backfill a trickle, not a burst. */
        private const val UPLOAD_CONTINUATION_DELAY_MIN = 15L

        /** Enqueue a background, restart-surviving, auto-retrying sync of [type]. */
        fun enqueue(context: Context, type: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<YtmSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TYPE to type))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                // ACELERADO: esto se pide casi siempre desde un botón que él acaba de tocar, y esperar
                // a que WorkManager encuentre un hueco es parte de la sensación de “no hizo nada”.
                // RUN_AS_NON_EXPEDITED_WORK_REQUEST y no un fallo cuando se acaba la cuota del sistema:
                // una sincronización normal y tardía sigue siendo una sincronización.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // KEEP: if a sync of this type is already queued/running, don't pile up duplicates — the
            // running one already covers it (and WorkManager will keep retrying it to completion).
            WorkManager.getInstance(context)
                .enqueueUniqueWork("ytm_sync_$type", ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Continue a library upload that ran out of budget. REPLACE (not KEEP) because the previous
         * hop has finished by the time this is called, and the unique name keeps the chain single —
         * two overlapping upload chains could double the request cost.
         * Battery-friendly by construction: network-constrained, not expedited, and delayed.
         */
        private fun enqueueUploadContinuation(context: Context, hop: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<YtmSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(UPLOAD_CONTINUATION_DELAY_MIN, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_TYPE to TYPE_UPLOAD_LIBRARY, KEY_UPLOAD_HOP to hop))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "ytm_sync_$TYPE_UPLOAD_LIBRARY",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
