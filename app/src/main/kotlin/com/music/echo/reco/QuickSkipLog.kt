package iad1tya.echo.music.reco

import android.content.Context
import java.io.File

/**
 * Row 720 — the quick skips the taste model never saw.
 *
 * The listen history only records a play after the history threshold (Ajustes ▸ Reproductor, 30 s by default) and
 * not while it is paused, so a song the user left after a few seconds never became an event and [AffinityEngine] —
 * whose «skip» is a short play it can see — never learned from it. The smart queue's own memories
 * ([SessionSkipMemory], [ContinuationSkipMemory]) only steer the continuation. This keeps those skips in a small local
 * log that [AffinityEngine.buildProfile] folds in like the plays it never got (Home, radio, smart shuffle).
 *
 * Only the user's own move to the NEXT song counts ([isUserQuickSkip]): not an error or a skip the app made, not a
 * natural advance, not a jump elsewhere in the queue, not while history is paused, as a Listen Together guest, while
 * casting or mirroring another device, nor in a «Pedir música» queue (the user is trying out what he asked for).
 * Bounded at [CAP] entries (oldest dropped first) in one small private file, written off the main thread only when a
 * skip happens. Ids and artist names stay in that file: nothing here is logged.
 */
object QuickSkipLog {
    const val CAP = 500
    const val FILE_NAME = "quick_skip_log.tsv"

    /** The context id «Pedir música» gives its queues (`MusicRequestViewModel`, `"MR:" + UUID`). */
    const val MUSIC_REQUEST_CONTEXT_PREFIX = "MR:"

    data class Entry(
        val songId: String,
        val artistId: String?,
        val artistName: String?,
        val atMs: Long,
        val listenedMs: Long,
        val durationMs: Long,
    )

    /** Everything [isUserQuickSkip] needs about a seek, read by the service when it happens. */
    data class SeekFacts(
        val toAnotherSong: Boolean,
        val toNextInOrder: Boolean,
        val listenedMs: Long,
        val durationMs: Long,
        val historyThresholdMs: Long,
        val appMovedOn: Boolean,
        val historyPaused: Boolean,
        val listenTogetherGuest: Boolean,
        val castingOrMirror: Boolean,
        val musicRequestQueue: Boolean,
    )

    /** Pure: a seek the taste model should learn from — the user left a song he heard for less than the history needs. */
    fun isUserQuickSkip(f: SeekFacts): Boolean =
        f.toAnotherSong && f.toNextInOrder &&
            f.listenedMs > 0 && f.listenedMs < f.historyThresholdMs &&
            (f.durationMs <= 0 || f.listenedMs < f.durationMs) &&
            !f.appMovedOn && !f.historyPaused && !f.listenTogetherGuest && !f.castingOrMirror && !f.musicRequestQueue

    fun isMusicRequestContext(contextId: String?): Boolean = contextId?.startsWith(MUSIC_REQUEST_CONTEXT_PREFIX) == true

    /** Pure: [entries] plus [entry], the oldest dropped beyond [cap]. */
    fun appended(entries: List<Entry>, entry: Entry, cap: Int = CAP): List<Entry> = (entries + entry).takeLast(cap)

    fun encode(e: Entry): String =
        listOf(e.songId.clean(), e.artistId.orEmpty().clean(), e.artistName.orEmpty().clean(), e.atMs, e.listenedMs, e.durationMs)
            .joinToString("\t")

    fun decode(line: String): Entry? {
        val p = line.split('\t')
        if (p.size != 6 || p[0].isBlank()) return null
        return Entry(
            songId = p[0],
            artistId = p[1].ifBlank { null },
            artistName = p[2].ifBlank { null },
            atMs = p[3].toLongOrNull() ?: return null,
            listenedMs = p[4].toLongOrNull() ?: return null,
            durationMs = p[5].toLongOrNull() ?: return null,
        )
    }

    private val lock = Any()

    fun read(file: File): List<Entry> = synchronized(lock) { readUnlocked(file) }

    /** Adds [entry] and rewrites the file through a temporary one, so a crash mid-write never leaves half a log. */
    fun append(file: File, entry: Entry, cap: Int = CAP) {
        synchronized(lock) {
            val text = appended(readUnlocked(file), entry, cap).joinToString("\n", transform = ::encode)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    /** When [clear] last ran (wall clock): a taste profile built before it still holds the skips he asked to forget. */
    @Volatile
    var clearedAtMs: Long = 0L
        private set

    /** «Borrar historial de escucha» forgets these too: they are listening history the events table never held. */
    fun clear(file: File) {
        synchronized(lock) {
            file.delete()
            File(file.parentFile, file.name + ".tmp").delete()
            clearedAtMs = System.currentTimeMillis()
        }
    }

    fun read(context: Context): List<Entry> = read(file(context))

    fun record(context: Context, entry: Entry) = append(file(context), entry)

    fun clear(context: Context) = clear(file(context))

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun readUnlocked(file: File): List<Entry> =
        if (!file.isFile) emptyList() else file.readLines().mapNotNull(::decode).takeLast(CAP)

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
