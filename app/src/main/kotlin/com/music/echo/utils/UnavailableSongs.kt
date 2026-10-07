package iad1tya.echo.music.utils

import android.content.Context
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterUnavailable
import com.music.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * The process-wide [UnavailableRegistry] (owner 2026-10-06: "cualquier canción que ya no esté disponible
 * en YouTube Music no se la muestres al usuario… para no causar errores de reproducción"), persisted in
 * its own small SharedPreferences file and published as [ids] so the screens can hide and the player can
 * skip. Never deletes anything: a library row, a playlist entry or a Me gusta stays exactly where it is.
 *
 * Logs only counts (AGENTS.md rule 4) — never an id, title or artist.
 */
object UnavailableSongs {

    private const val PREFS = "unavailable_songs"
    private const val KEY = "entries_v1"

    private val registry = UnavailableRegistry()
    private val _ids = MutableStateFlow<Set<String>>(emptySet())

    /** The ids hidden from lists and skipped by the queue right now. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    @Volatile private var prefs: android.content.SharedPreferences? = null

    /** Loads the persisted marks once. Cheap after the first call; call it off the main thread. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            registry.restore(runCatching { p.getString(KEY, null) }.getOrNull())
            prefs = p
            _ids.value = registry.snapshot()
        }
    }

    fun isUnavailable(id: String?): Boolean = id != null && registry.isUnavailable(id)

    /**
     * A YouTube Music list just arrived: learn which of its songs are greyed out and which are back.
     * Call it where the list is LOADED (view model, sync), never per recomposition.
     */
    fun learnFrom(items: List<SongItem>) {
        if (items.isEmpty()) return
        val greyed = items.filter { it.unavailable }.map { it.id }
        val shown = items.filterNot { it.unavailable }.map { it.id }
        val changed = registry.markGreyedOut(greyed) or registry.markSeenAvailable(shown)
        if (changed) {
            Timber.i("UNAVAILABLE learned greyed=%d listSize=%d", greyed.size, items.size)
            persist()
        }
    }

    /**
     * Search results, suggestions and artist pages: learns only the songs marked greyed out — it never
     * CLEARS a mark from them, because those responses may simply omit the flag — and returns the ids
     * hidden right now.
     */
    fun learnGreyedOnly(items: List<YTItem>): Set<String> {
        val greyed = items.filter { it is SongItem && it.unavailable }.map { it.id }
        if (greyed.isNotEmpty() && registry.markGreyedOut(greyed)) {
            Timber.i("UNAVAILABLE learned greyed=%d from search/artist results", greyed.size)
            persist()
        }
        return ids.value
    }

    /** [items] without songs or videos known to be unavailable (learning the greyed ones first). */
    fun <T : YTItem> playableOnly(items: List<T>): List<T> {
        if (items.isEmpty()) return items
        val kept = items.filterUnavailable(learnGreyedOnly(items))
        return if (kept.size == items.size) items else kept
    }

    /** [playableOnly] for a search summary; a section left empty is dropped. */
    fun playableOnly(page: SearchSummaryPage): SearchSummaryPage =
        page.filterUnavailable(learnGreyedOnly(page.summaries.flatMap { it.items }))

    /** A playback failure that YouTube attributed to the CONTENT (pending until another song plays). */
    fun recordFailure(id: String?) {
        if (id.isNullOrBlank()) return
        registry.recordFailure(id)
        Timber.i("UNAVAILABLE content failure pending (confirmed only if another song plays next)")
    }

    /** [id] resolved and played: confirms earlier content failures of other songs, and clears [id]. */
    fun recordSuccess(id: String?) {
        if (id.isNullOrBlank()) return
        if (registry.recordSuccess(id)) {
            persist()
            Timber.i("UNAVAILABLE marks now=%d", _ids.value.size)
        }
    }

    /**
     * For a queue built from a YouTube Music list: learns the list, then drops every song that is greyed
     * out or known unavailable — EXCEPT the one at [startIndex], which the user picked (the player then
     * explains and skips it, as before). Returns the kept songs and the start index remapped onto them.
     */
    fun keepPlayable(items: List<SongItem>, startIndex: Int): Pair<List<SongItem>, Int> {
        learnFrom(items)
        val hidden = ids.value
        val kept = items.withIndex().filter { (i, song) ->
            i == startIndex || (!song.unavailable && song.id !in hidden)
        }
        if (kept.size == items.size) return items to startIndex
        return kept.map { it.value } to kept.indexOfFirst { it.index == startIndex }.coerceAtLeast(0)
    }

    /**
     * Library rows ([iad1tya.echo.music.db.entities.Song]) without the ones known to be unavailable — a
     * DOWNLOADED song is always kept: it plays from disk, YouTube's verdict does not touch it. Display
     * only: the rows themselves (Me gusta, library, playlist maps) are never modified.
     */
    fun hideUnavailable(
        songs: List<iad1tya.echo.music.db.entities.Song>,
        hidden: Set<String> = ids.value,
    ): List<iad1tya.echo.music.db.entities.Song> = hideUnavailableBy(songs, hidden) { it }

    /** [hideUnavailable] for rows that wrap a library song (a playlist entry, …). */
    fun <T> hideUnavailableBy(
        items: List<T>,
        hidden: Set<String> = ids.value,
        songOf: (T) -> iad1tya.echo.music.db.entities.Song,
    ): List<T> {
        if (hidden.isEmpty() || items.isEmpty()) return items
        val out = items.filterNot { item ->
            val song = songOf(item)
            song.id in hidden && !song.song.isDownloaded && song.song.dateDownload == null
        }
        return if (out.size == items.size) items else out
    }

    /** [items] without the ones known to be unavailable. Same list instance when nothing is hidden. */
    fun <T> visible(items: List<T>, hidden: Set<String> = ids.value, idOf: (T) -> String?): List<T> {
        if (hidden.isEmpty() || items.isEmpty()) return items
        val out = items.filterNot { item -> idOf(item)?.let { it in hidden } == true }
        return if (out.size == items.size) items else out
    }

    private fun persist() {
        _ids.value = registry.snapshot()
        val p = prefs ?: return
        runCatching { p.edit().putString(KEY, registry.serialize()).apply() }
    }
}
