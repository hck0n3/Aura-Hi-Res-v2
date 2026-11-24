package iad1tya.echo.music.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import timber.log.Timber

/**
 * Registry row 838 — keeps the «Revisar suscripciones» run ([SubscriptionCleanupRunner]) going with the app in the
 * background: a foreground job (the pattern and the silent channel of [YtmSyncWorker]) with the notification «Quitando
 * suscripciones: X de N». It does no work itself and never polls: it follows the runner's state and ends as soon as the
 * run is no longer active (regla 7: it exists only while removing).
 *
 * Row 839: the notification carries «Detener». It cancels this job ([WorkManager.createCancelPendingIntent]); the job,
 * stopped by the app, asks the runner to stop the run the same way the screen's «Detener» does (the item in flight
 * finishes its wait for the account, the rest is not attempted). A stop by the system instead (quota, constraints)
 * leaves the run going in the app's process.
 */
class SubscriptionCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SubscriptionCleanupEntryPoint {
        fun runner(): SubscriptionCleanupRunner
    }

    private val runner: SubscriptionCleanupRunner
        get() = EntryPointAccessors.fromApplication(applicationContext, SubscriptionCleanupEntryPoint::class.java).runner()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        runner.state.value.let { foregroundInfo(it.done, it.total, it.stopping) }

    override suspend fun doWork(): Result {
        val runner = runner
        if (!runner.state.value.active) return Result.success()
        var foreground = true
        try {
            runner.state
                .takeWhile { it.active }
                .map { Triple(it.done, it.total, it.stopping) }
                .distinctUntilChanged()
                .collect { (done, total, stopping) ->
                    if (!foreground) return@collect
                    try {
                        setForeground(foregroundInfo(done, total, stopping))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Android 12+ can refuse a foreground start from the background: the run still goes on in the
                        // app's process, just without the notification. Asked once, not on every item.
                        foreground = false
                        Timber.w("SUB_CLEANUP_FOREGROUND failed: %s", e.javaClass.simpleName)
                    }
                }
        } catch (e: CancellationException) {
            // Row 839: the notification's «Detener» (only this app cancels this job).
            if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) runner.stop()
            throw e
        }
        return Result.success()
    }

    private fun foregroundInfo(done: Int, total: Int, stopping: Boolean): ForegroundInfo {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService<NotificationManager>()
            if (nm?.getNotificationChannel(YtmSyncWorker.SYNC_CHANNEL_ID) == null) {
                nm?.createNotificationChannel(
                    NotificationChannel(
                        YtmSyncWorker.SYNC_CHANNEL_ID,
                        ctx.getString(R.string.sync_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
        val notification = NotificationCompat.Builder(ctx, YtmSyncWorker.SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentTitle(ctx.getString(R.string.sub_cleanup_title))
            .setContentText(
                if (stopping) {
                    ctx.getString(R.string.sub_cleanup_stopping)
                } else {
                    ctx.getString(R.string.sub_cleanup_notification_progress, done, total)
                },
            )
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (!stopping) {
                    addAction(
                        R.drawable.close,
                        ctx.getString(R.string.sub_cleanup_stop),
                        WorkManager.getInstance(ctx).createCancelPendingIntent(this@SubscriptionCleanupWorker.id),
                    )
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val UNIQUE_NAME = "subscription_cleanup"
        private const val NOTIFICATION_ID = 0x5C11

        /**
         * Called by [SubscriptionCleanupRunner] when a run starts. APPEND_OR_REPLACE: a worker still finishing the
         * previous run is followed by this one (which sees the new run), a cancelled one is replaced.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SubscriptionCleanupWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
