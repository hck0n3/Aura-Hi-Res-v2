package iad1tya.echo.music.listentogether

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.RoomDurationRepairDoneKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Registry 765: before commits 534ea86 / e82407b a Listen Together guest stored the room's duration (milliseconds)
 * in the song's seconds field, so those songs show and sort 1000x too long. New items are right; this repairs the
 * rows already stored, once per install.
 *
 * Only values of a day or more are divided by 1000: a real streamed song or video is not that long, and a real 3-hour
 * item is far below the bar. Local files and uploads are left alone: an audiobook can really last that long. A song
 * shorter than 86.4 s stored in milliseconds stays below the bar and is not repaired (the safe direction).
 */
object RoomDurationRepair {
    /** One day, in seconds. A real 3-hour item (10 800 s) is far below it. */
    const val MIN_SECONDS_STORED_AS_MILLIS = 86_400

    /** Mirrors the WHERE clause of `DatabaseDao.repairMillisecondDurations`. */
    fun needsRepair(durationSeconds: Int, isLocal: Boolean, isUploaded: Boolean): Boolean =
        !isLocal && !isUploaded && durationSeconds >= MIN_SECONDS_STORED_AS_MILLIS

    /** Mirrors the SET clause (SQLite integer division). */
    fun repaired(durationSeconds: Int): Int = durationSeconds / 1000

    /** Runs the repair once; the flag is set only after the update succeeded. Call off the main thread. */
    suspend fun runOnce(context: Context, database: MusicDatabase) {
        val store = context.dataStore
        if (store.data.first()[RoomDurationRepairDoneKey] == true) return
        val fixed = database.repairMillisecondDurations(MIN_SECONDS_STORED_AS_MILLIS)
        store.edit { it[RoomDurationRepairDoneKey] = true }
        // Privacy (AGENTS rule 4): a count only.
        Timber.tag("RoomDurationRepair").i("LT_DURATION_REPAIR fixed=%d", fixed)
    }
}
