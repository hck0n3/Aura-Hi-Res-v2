package iad1tya.echo.music.utils

/**
 * Row 678 (owner 2026-11-23): Google account sync is OFF by default on NEW installs — no download sync
 * (`YtmSyncKey`), no automatic sync (`YtmAutoSyncFreqDaysKey` = 0) and no upload ([LibraryUploadOptIn.decide]).
 *
 * Every other install keeps exactly what it was running on: a stored value is never touched, and a value that
 * was never written is written as the OLD default (download sync on, every 3 days), so the change cannot reach
 * an existing user silently. `App.applyGoogleSyncDefaultsV1` writes the result once per install.
 */
object GoogleSyncDefaults {

    fun downloadSync(stored: Boolean?, freshInstall: Boolean): Boolean = stored ?: !freshInstall

    fun autoSyncFreqDays(stored: Int?, freshInstall: Boolean): Int =
        stored ?: if (freshInstall) 0 else YtmAutoSyncWorker.DEFAULT_FREQ_DAYS
}
