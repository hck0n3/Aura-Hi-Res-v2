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
        assertTrue(doWork.contains("val toInsert = ReleaseRadarRetention.withoutDuplicateAlbums(known.values, items)"))
        assertTrue(doWork.contains("if (toInsert.isNotEmpty()) insertNewReleasesIgnore(toInsert)"))
        assertTrue(doWork.contains("database.pruneExpiredReleases("))
        assertTrue(worker.contains("private const val WINDOW_DAYS = ReleaseRadarRetention.MAX_AGE_DAYS"))
        // Review: the one-time replacement and the fallback-row upgrade run inside ONE transaction (no empty flash).
        val tx = doWork.substringAfter("database.withTransaction {").substringBefore("if (migratedNow)")
        assertTrue(tx.contains("clearReleases()") && tx.contains("deleteReleasesByIds(upgrade.deleteIds)") && tx.contains("upsertReleases(upgrade.upserts)"))
        // Review: a transient token failure does not run the YouTube fallback over a table with Spotify rows.
        assertTrue(doWork.contains("} else if (spotifySessionStored() && known.values.any { it.source != ReleaseRadarRetention.SOURCE_YT }) {"))
        assertTrue(doWork.contains("val upgrade = ReleaseRadarRetention.upgradeYtRows(known.values, items)"))
        // Battery (rule 7): a release already stored is not searched on YouTube again; the 6 h gate stays (row 32).
        assertTrue(worker.contains("val key = ReleaseRadarMatching.dedupeKey(hit.artistName, hit.album.name)\n                    val stored = known[key]"))
        // Review: only the newest per artist inside the cap is searched; an older one only once, and only once YouTube answered.
        assertTrue(worker.contains("val allHits = ReleaseRadarMatching.newestPerArtistRanked("))
        assertTrue(worker.contains("if (!ReleaseRadarRetention.needsLookup(hit.date, today, lookups.containsKey(key))) return@async null"))
        assertTrue(worker.contains("matchSpotifyToYoutubeAlbum(hit.album.name, hit.artistName) { lookups[key] = today.toEpochDay() }"))
        assertTrue(worker.contains("YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull() ?: return null\n        onAnswered()"))
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
        // Review: seen when the card is really on screen (a lazy list precomposes the next card ahead of time).
        assertTrue(screen.contains("SeenWhenVisible(hero.id, markShown) {"))
        assertTrue(screen.contains("SeenWhenVisible(heroRest[index].id, markShown) {"))
        assertTrue(screen.contains("SeenWhenVisible(shown.radarAlbums[index].id, markShown) {"))
        assertTrue(screen.contains(".onVisibilityChanged(minFractionVisible = SEEN_MIN_FRACTION_VISIBLE) { if (it) onShown(album.id) }"))
        assertTrue(screen.contains("Box(Modifier.onVisibilityChanged(minFractionVisible = SEEN_MIN_FRACTION_VISIBLE) { if (it) onShown(id) }) {"))
        assertFalse("vuelve a marcar al componer", Regex("""LaunchedEffect\([^)]*\)\s*\{\s*(markShown|onShown)""").containsMatchIn(screen))
        assertTrue(screen.contains("DisposableEffect(Unit) { onDispose { viewModel.endRadarVisit() } }"))
        val classic = kt("ui/screens/library/ReleaseRadarScreen.kt")
        assertTrue(classic.contains(".onVisibilityChanged { if (it) viewModel.markReleaseShown(item.playId) }"))
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
        // Review: an empty radar is the goal state — Home's shelf empties; only a failed read keeps it.
        assertTrue(home.contains("}.getOrNull() ?: return"))
        val homeAll = kt("viewmodels/HomeViewModel.kt").substringAfter("private suspend fun getNewFromArtists()").substringBefore("private suspend fun loadSimilarRecommendations()")
        assertFalse(homeAll.contains("albums.isNotEmpty() || newFromArtists.value == null"))
    }

    // ---- review of row 674: bounded lookups and Spotify rows replacing fallback rows ----------------

    @Test
    fun `a new release is searched every pass only while it is young, an older one once`() {
        assertEquals(7L, ReleaseRadarRetention.LOOKUP_RETRY_DAYS)
        assertTrue(ReleaseRadarRetention.needsLookup(today.minusDays(30), today, alreadyLookedUp = false))
        assertFalse(ReleaseRadarRetention.needsLookup(today.minusDays(30), today, alreadyLookedUp = true))
        assertTrue(ReleaseRadarRetention.needsLookup(today.minusDays(7), today, alreadyLookedUp = true))
        assertFalse(ReleaseRadarRetention.needsLookup(today.minusDays(8), today, alreadyLookedUp = true))
    }

    @Test
    fun `stored lookups round-trip and drop what is older than the window`() {
        val day = today.toEpochDay()
        val stored = ReleaseRadarRetention.writeLookups(mapOf("adele|hello" to day, "old|x" to day - 61, "edge|y" to day - 60))
        val read = ReleaseRadarRetention.readLookups(stored + "garbage", today)
        assertEquals(mapOf("adele|hello" to day, "edge|y" to day - 60), read)
    }

    @Test
    fun `only the newest release per artist inside the cap is worth a search`() {
        data class H(val artist: String, val date: LocalDate, val followed: Boolean)
        val hits = listOf(
            H("A", today.minusDays(1), true), H("a", today.minusDays(20), true),
            H("B", today.minusDays(3), false), H("C", today.minusDays(5), true),
        )
        val picked = ReleaseRadarMatching.newestPerArtistRanked(hits, { it.artist }, { it.date }, { it.followed }, cap = 2)
        assertEquals(listOf(H("A", today.minusDays(1), true), H("C", today.minusDays(5), true)), picked)
    }

    @Test
    fun `a Spotify row replaces the fallback row of the same release and keeps seen`() {
        val jan1 = LocalDate.of(today.year, 1, 1)
        val ytSame = row("k1", released = jan1, source = "yt", fetched = today.minusDays(10)).copy(seen = true, type = "Release")
        val ytOtherSpelling = row("k2-yt", released = jan1, source = "yt", playId = "MPREb_k2")
        val ytUnrelated = row("k3", released = jan1, source = "yt")
        val known = listOf(ytSame, ytOtherSpelling, ytUnrelated)
        val items = listOf(
            row("k1", released = today.minusDays(2)).copy(type = "single"),
            row("k2", released = today.minusDays(4), playId = "MPREb_k2"),
        )
        val up = ReleaseRadarRetention.upgradeYtRows(known, items)
        assertEquals(listOf("k2-yt"), up.deleteIds)
        val k1 = up.upserts.single { it.id == "k1" }
        assertEquals("spotify", k1.source)
        assertEquals("single", k1.type)
        assertEquals(today.minusDays(2).atStartOfDay(), k1.releaseDate)
        assertTrue("conserva visto", k1.seen)
        assertEquals(ytSame.fetchedAt, k1.fetchedAt)
        assertEquals("MPREb_k2", up.upserts.single { it.id == "k2" }.playId)
        // No fallback rows → nothing to do.
        assertTrue(ReleaseRadarRetention.upgradeYtRows(listOf(row("s")), items).upserts.isEmpty())
    }

    @Test
    fun `a fallback row of an album already held under another id is not inserted`() {
        val known = listOf(row("spotify-key", playId = "MPREb_x"))
        val items = listOf(
            row("yt-key", source = "yt", playId = "MPREb_x"),
            row("yt-new", source = "yt", playId = "MPREb_y"),
            row("spotify-key", source = "yt", playId = "MPREb_x"),
        )
        assertEquals(listOf("yt-new", "spotify-key"), ReleaseRadarRetention.withoutDuplicateAlbums(known, items).map { it.id })
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
