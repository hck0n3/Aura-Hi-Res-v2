package iad1tya.echo.music.ui.player

import android.content.Context
import iad1tya.echo.music.canvas.CanvasArtwork
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Row 523 (owner 2026-10-29: «las portadas animadas de los álbumes se tienen que aplicar a todas las canciones del mismo
 * álbum sin estar descargando de nuevo cada portada por canción… una vez que se descargue, sea animada o no, de largo se aplica
 * a todas… y las animadas se guardan en el caché para reutilizarse y ahorrar datos»).
 *
 * The player looked a motion cover up PER SONG: [CanvasArtworkPlaybackCache] is keyed by media id, so every song of an album
 * ran the whole provider chain again (up to a dozen searches when the album has none), and nothing survived a restart. This
 * remembers the ANSWER PER ALBUM instead (artist + album; artist + song when the song has no album):
 *  · a found cover is kept 7 days, in memory and in `filesDir/canvas_album_memo.json`, so after the first song of an album the
 *    rest — and the next day's session — need no request at all. The video itself is already kept by [CanvasVideoCache];
 *  · «this album has none» is kept 20 minutes in memory only, because a lookup that failed (offline, expired Apple key) looks the
 *    same and must retry soon. Two songs of the same album in a row cost ONE lookup between them.
 * Only names are keyed (lower-case letters and digits); the file holds the cover URLs the providers returned, nothing else.
 */
object CanvasAlbumMemo {
    sealed interface Answer {
        data class Hit(val artwork: CanvasArtwork) : Answer
        data object None : Answer
    }

    @Serializable
    private data class Stored(val artwork: CanvasArtwork, val savedAtMs: Long)

    private const val FILE = "canvas_album_memo.json"
    private const val MAX_ENTRIES = 300
    private const val HIT_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val NONE_TTL_MS = 20L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val hits = LinkedHashMap<String, Stored>(64, 0.75f, true)
    private val nones = HashMap<String, Long>()
    private var loaded = false

    /** The key for [artist] + [album] (or + [song] when there is no album), or null when there is nothing to key on. */
    fun key(artist: String?, album: String?, song: String?): String? {
        val a = norm(artist)
        if (a.isEmpty()) return null
        val al = norm(album)
        if (al.isNotEmpty()) return "al|$a|$al"
        val s = norm(song)
        return if (s.isEmpty()) null else "so|$a|$s"
    }

    @Synchronized
    fun lookup(context: Context, key: String, now: Long = System.currentTimeMillis()): Answer? {
        ensureLoaded(context)
        hits[key]?.let { stored ->
            if (now - stored.savedAtMs < HIT_TTL_MS) return Answer.Hit(stored.artwork)
            hits.remove(key)
        }
        nones[key]?.let { until ->
            if (now < until) return Answer.None
            nones.remove(key)
        }
        return null
    }

    /** [artwork] null = the lookup ANSWERED «none» (never call this for a timeout). */
    @Synchronized
    fun remember(context: Context, key: String, artwork: CanvasArtwork?, now: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        if (artwork == null || artwork.preferredAnimationUrl.isNullOrBlank()) {
            nones[key] = now + NONE_TTL_MS
            return
        }
        nones.remove(key)
        hits[key] = Stored(artwork, now)
        while (hits.size > MAX_ENTRIES) {
            val oldest = hits.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
        persist(context)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE)
            if (file.isFile) {
                json.decodeFromString<Map<String, Stored>>(file.readText()).forEach { (k, v) -> hits[k] = v }
            }
        }
    }

    private fun persist(context: Context) {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE)
            file.writeText(json.encodeToString<Map<String, Stored>>(hits.toMap()))
        }
    }

    private fun norm(s: String?): String =
        s.orEmpty().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
