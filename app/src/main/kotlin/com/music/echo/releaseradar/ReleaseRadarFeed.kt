package iad1tya.echo.music.releaseradar

import android.content.Context
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** What a radar screen draws: the cumulative list and whether to say «Estás al día». */
data class RadarView(val releases: List<ReleaseRadarItem>, val caughtUp: Boolean) {
    companion object {
        val EMPTY = RadarView(emptyList(), caughtUp = false)
    }
}

/**
 * The radar table as a screen shows it (Novedades and the classic radar screen), with the per-release "seen" writes.
 * Owner 2026-11-23: a release is marked seen when ITS card is shown ([markShown]), no longer every row on entry.
 * Writes are batched (one UPDATE per burst of cards entering the screen, not one per card), and only rows still
 * unseen are touched, so a card shown again writes nothing.
 */
@OptIn(FlowPreview::class)
class ReleaseRadarFeed(
    private val context: Context,
    private val database: MusicDatabase,
    private val scope: CoroutineScope,
) {
    private val shownThisVisit = MutableStateFlow<Set<String>>(emptySet())
    private val pendingSeen = MutableStateFlow<Set<String>>(emptySet())

    val view: Flow<RadarView> = combine(
        database.releasesByDateDesc(),
        database.retiredReleaseIds(),
        shownThisVisit,
    ) { rows, retired, shown ->
        val visible = ReleaseRadarRetention.visible(rows, retired.toSet(), shown, LocalDate.now())
        RadarView(visible, ReleaseRadarRetention.caughtUp(rows, visible, ReleaseRadarWorker.hadReleases(context)))
    }

    init {
        scope.launch(Dispatchers.IO) {
            pendingSeen.debounce(SEEN_WRITE_DEBOUNCE_MS).collect { if (it.isNotEmpty()) flushSeen() }
        }
    }

    /** A release's card (by its playId, the album browseId) is on screen. */
    fun markShown(playId: String) {
        if (playId.isBlank() || playId in shownThisVisit.value) return
        shownThisVisit.update { it + playId }
        pendingSeen.update { it + playId }
    }

    /** The screen left: write what is pending, and let retired releases go on the next visit. */
    fun endVisit() {
        scope.launch(Dispatchers.IO) { flushSeen() }
        shownThisVisit.value = emptySet()
    }

    private suspend fun flushSeen() {
        val ids = pendingSeen.getAndUpdate { emptySet() }
        if (ids.isEmpty()) return
        runCatching { database.markReleasesSeen(ids.toList()) }
            .onFailure { Timber.w(it, "Release radar: marking releases seen failed") }
    }

    private companion object {
        const val SEEN_WRITE_DEBOUNCE_MS = 500L
    }
}
