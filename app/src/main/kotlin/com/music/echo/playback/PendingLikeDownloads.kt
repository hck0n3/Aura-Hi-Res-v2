package iad1tya.echo.music.playback

import android.content.Context

/**
 * Row 519: songs the user liked on a metered network (mobile data) whose bytes are NOT already in the listen cache. The
 * automatic download used to be dropped there for good (HALLAZGO-037: it must not burn mobile data), so the heart «did
 * nothing». They wait here, on disk so a restart keeps them, and [DownloadUtil.flushPendingLikeDownloads] enqueues them as
 * soon as an unmetered network is back. Titles only travel to the DownloadRequest; nothing here is ever logged.
 */
internal object PendingLikeDownloads {
    data class Entry(val id: String, val title: String, val isVideoSong: Boolean)

    private const val PREFS = "aura_pending_like_downloads"
    private const val KEY = "entries"
    private const val MAX = 200

    @Synchronized
    fun add(context: Context, entry: Entry) {
        val all = read(context).filterNot { it.id == entry.id } + entry
        write(context, all.takeLast(MAX))
    }

    @Synchronized
    fun drain(context: Context): List<Entry> {
        val all = read(context)
        if (all.isNotEmpty()) write(context, emptyList())
        return all
    }

    @Synchronized
    fun count(context: Context): Int = read(context).size

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): List<Entry> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3 || parts[0].isBlank()) null else Entry(parts[0], parts[2], parts[1] == "1")
        }

    private fun write(context: Context, entries: List<Entry>) {
        prefs(context).edit()
            .putStringSet(KEY, entries.map { "${it.id}\t${if (it.isVideoSong) "1" else "0"}\t${it.title.replace('\t', ' ').replace('\n', ' ')}" }.toSet())
            .apply()
    }
}
