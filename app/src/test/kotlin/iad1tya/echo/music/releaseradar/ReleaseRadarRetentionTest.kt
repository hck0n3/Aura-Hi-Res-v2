package iad1tya.echo.music.releaseradar

import iad1tya.echo.music.db.entities.ReleaseRadarItem
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-23 (registry rows 674/675): cumulative Release Radar. A release stays in Novedades until it was SEEN
 * there AND one of its songs was PLAYED, for at most 60 days; with nothing left, «Estás al día». Seen = its card was
 * shown, no longer every row on opening the tab (row 571).
 */
class ReleaseRadarRetentionTest {

    private val today = LocalDate.of(2026, 11, 23)

    private fun row(
        id: String,
        released: LocalDate = today,
        source: String = "spotify",
        fetched: LocalDate = today,
        playId: String = "MPREb_$id",
    ) = ReleaseRadarItem(
        id = id, artistId = "", title = id, artist = "a", type = "album",
        releaseDate = released.atStartOfDay(), artworkUri = null, source = source, playId = playId,
        fetchedAt = fetched.atTime(12, 0),
    )

    @Test
    fun `a release stays until it is seen and played`() {
        val rows = listOf(row("new"), row("done"))
        assertEquals(listOf("new"), ReleaseRadarRetention.visible(rows, setOf("done"), emptySet(), today).map { it.id })
        assertEquals(listOf("new", "done"), ReleaseRadarRetention.visible(rows, emptySet(), emptySet(), today).map { it.id })
    }

    @Test
    fun `a card shown during this visit never vanishes under the finger`() {
        val rows = listOf(row("done"))
        assertEquals(listOf("done"), ReleaseRadarRetention.visible(rows, setOf("done"), setOf("MPREb_done"), today).map { it.id })
    }

    @Test
    fun `the age limit is 60 days from the exact release date`() {
        assertEquals(60L, ReleaseRadarRetention.MAX_AGE_DAYS)
        val rows = listOf(row("edge", released = today.minusDays(60)), row("old", released = today.minusDays(61)))
        assertEquals(listOf("edge"), ReleaseRadarRetention.visible(rows, emptySet(), emptySet(), today).map { it.id })
        // Even a card shown this visit goes once it is too old.
        assertTrue(ReleaseRadarRetention.visible(rows.drop(1), emptySet(), setOf("MPREb_old"), today).isEmpty())
    }

    @Test
    fun `year-only YouTube rows age from the day the radar first stored them`() {
        val jan1 = LocalDate.of(today.year, 1, 1)
        val fresh = row("fresh", released = jan1, source = "yt", fetched = today.minusDays(3))
        val stale = row("stale", released = jan1, source = "yt", fetched = today.minusDays(61))
        assertEquals(listOf("fresh"), ReleaseRadarRetention.visible(listOf(fresh, stale), emptySet(), emptySet(), today).map { it.id })
    }

    @Test
    fun `caught up only when the radar has had releases`() {
        val rows = listOf(row("done"))
        assertTrue(ReleaseRadarRetention.caughtUp(rows, emptyList(), hadReleases = false))
        assertTrue(ReleaseRadarRetention.caughtUp(emptyList(), emptyList(), hadReleases = true))
        assertFalse("nunca cargó: sigue la pista de siempre", ReleaseRadarRetention.caughtUp(emptyList(), emptyList(), hadReleases = false))
        assertFalse(ReleaseRadarRetention.caughtUp(rows, rows, hadReleases = true))
    }

    @Test
    fun `prune deletes only what can no longer be fetched again`() {
        assertEquals(today.minusDays(60), ReleaseRadarRetention.pruneThreshold(today))
        assertEquals(LocalDate.of(2026, 1, 1), ReleaseRadarRetention.ytPruneThreshold(today))
    }

    @Test
    fun `the table lookup key is the candidate key`() {
        val c = ReleaseCandidate("Hello!", "ADELE", today, "spotify", null, "p")
        assertEquals(ReleaseRadarMatching.dedupeKey(c), ReleaseRadarMatching.dedupeKey("adele", "hello"))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun kt(path: String) = main("kotlin/com/music/echo/$path")

    @Test
    fun `the worker adds and prunes instead of wiping every run`() {
        val worker = kt("releaseradar/ReleaseRadarWorker.kt")
        val doWork = worker.substringAfter("override suspend fun doWork()").substringBefore("// ── Spotify path")
        // The only clearReleases left is the one-time replacement of the old weekly table.
        assertEquals(1, Regex("""clearReleases\(\)""").findAll(doWork).count())
        assertTrue(doWork.contains("if (!prefs.getBoolean(CUMULATIVE_MIGRATED_KEY, false))"))
        assertTrue(doWork.contains("} else if (items.isNotEmpty()) {\n                database.insertNewReleasesIgnore(items)"))
        assertTrue(doWork.contains("database.pruneExpiredReleases("))
        assertTrue(worker.contains("private const val WINDOW_DAYS = ReleaseRadarRetention.MAX_AGE_DAYS"))
        // Battery (rule 7): a release already stored is not searched on YouTube again; the 6 h gate stays (row 32).
        assertTrue(worker.contains("known[ReleaseRadarMatching.dedupeKey(hit.artistName, hit.album.name)]"))
        assertTrue(worker.contains("const val AUTO_REFRESH_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L"))
        // Row 653: the type still comes from Spotify, also for a release reused from the table.
        assertEquals(2, Regex("""ReleaseRadarMatching\.releaseType\(hit\.album\.albumType, hit\.album\.totalTracks, hit\.album\.name\)""").findAll(worker).count())
        // Row 366: only followed artists are scanned.
        assertTrue(worker.contains("val scanArtists = followedSpotify.map { it to true }"))
    }

    @Test
    fun `nothing marks every release seen on entry, each card marks its own`() {
        for (vm in listOf("viewmodels/NovedadesViewModel.kt", "viewmodels/ReleaseRadarViewModel.kt")) {
            val s = kt(vm)
            assertFalse("$vm vuelve a marcar todo como visto", s.contains("markAllReleasesSeen()"))
            assertTrue(s.contains("ReleaseRadarFeed(context, database, viewModelScope)"))
        }
        val screen = kt("ui/newui/AuraNovedadesScreen.kt")
        assertTrue(screen.contains("LaunchedEffect(hero.id) { markShown(hero.id) }"))
        assertTrue(screen.contains("LaunchedEffect(heroRest[index].id) { markShown(heroRest[index].id) }"))
        assertTrue(screen.contains("LaunchedEffect(shown.radarAlbums[index].id) { markShown(shown.radarAlbums[index].id) }"))
        assertTrue(screen.contains("LaunchedEffect(album.id) { onShown(album.id) }"))
        assertTrue(screen.contains("DisposableEffect(Unit) { onDispose { viewModel.endRadarVisit() } }"))
        val classic = kt("ui/screens/library/ReleaseRadarScreen.kt")
        assertTrue(classic.contains("LaunchedEffect(item.playId) { viewModel.markReleaseShown(item.playId) }"))
        assertTrue(classic.contains("DisposableEffect(Unit) { onDispose { viewModel.endRadarVisit() } }"))
        // Row 571: the user's pull still runs the radar worker; entry stays behind the 6 h gate.
        val vm = kt("viewmodels/NovedadesViewModel.kt")
        assertTrue(vm.contains("ReleaseRadarWorker.runNow(context)") && vm.contains("ReleaseRadarWorker.refreshIfStale(context)"))
    }

    @Test
    fun `Estas al dia is shown apart from the never-loaded hint, and Home shows the same set`() {
        val screen = kt("ui/newui/AuraNovedadesScreen.kt")
        assertTrue(screen.contains("if (radarCaughtUp) {\n                                AuraEmpty(text = stringResource(R.string.novedades_caught_up))"))
        assertTrue(screen.contains("secondary = stringResource(R.string.novedades_empty_hint)"))
        assertTrue(main("res/values-es/echo_strings.xml").contains("<string name=\"novedades_caught_up\">Estás al día</string>"))
        assertTrue(main("res/values/echo_strings.xml").contains("<string name=\"novedades_caught_up\">"))
        val home = kt("viewmodels/HomeViewModel.kt").substringAfter("private suspend fun getNewFromArtists()").substringBefore("newFromArtists.value = albums")
        assertTrue(home.contains("ReleaseRadarRetention.visible("))
        assertTrue(home.contains("database.retiredReleaseIds().first()"))
    }

    @Test
    fun `retired means seen and a played song of that album`() {
        val dao = kt("db/DatabaseDao.kt")
        val q = dao.substringBefore("fun retiredReleaseIds(").substringAfterLast("@Query(")
        assertTrue(q.contains("r.seen = 1"))
        assertTrue(q.contains("JOIN event e ON e.songId = s.id WHERE s.albumId = r.playId"))
        assertTrue(q.contains("JOIN event e ON e.songId = m.songId WHERE m.albumId = r.playId"))
        val mark = dao.substringBefore("suspend fun markReleasesSeen(").substringAfterLast("@Query(")
        assertTrue(mark.contains("WHERE seen = 0 AND playId IN (:playIds)"))
    }
}
