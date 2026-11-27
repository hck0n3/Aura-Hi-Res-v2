package iad1tya.echo.music.stats

import iad1tya.echo.music.stats.ListeningStats.ArtistInfo
import iad1tya.echo.music.stats.ListeningStats.Play
import iad1tya.echo.music.stats.ListeningStats.SongInfo
import iad1tya.echo.music.stats.WrappedBuilder.Card
import iad1tya.echo.music.stats.WrappedBuilder.Personality
import iad1tya.echo.music.stats.WrappedBuilder.Story
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 910 — «Tu año en Aura»: the cards built from the year's numbers, the «too little data» case, the
 * «personalidad» line, when Inicio offers the card, and a source guard that nothing but the shared image leaves the phone.
 */
class WrappedBuilderTest {
    private val min = 60_000L
    private val year = ListeningStats.year(2026)

    private fun songs(n: Int) = (1..n).associate { i ->
        "s$i" to SongInfo("s$i", "Song $i", listOf(ArtistInfo("UC$i", "Artist $i")), "MPREb_$i", "Album $i", 180 + i * 7, null)
    }

    private fun plays(count: Int, minutesEach: Long, hour: Int = 15, songCount: Int = 6): List<Play> =
        (0 until count).map { i -> Play("s${i % songCount + 1}", LocalDateTime.of(2026, 3, 1 + i % 25, hour, 0), minutesEach * min) }

    private fun summary(plays: List<Play>, songCount: Int = 6, today: LocalDate? = null) =
        ListeningStats.summarize(year, plays, songs(songCount), today = today)

    @Test
    fun `too little data says so instead of empty cards`() {
        val few = WrappedBuilder.build(summary(plays(3, 20)))
        assertEquals(Story.TooLittle(plays = 3, minutes = 60), few)
        val short = WrappedBuilder.build(summary(plays(12, 1)))
        assertTrue("12 plays but only 12 minutes", short is Story.TooLittle)
        val nothing = WrappedBuilder.build(summary(emptyList()))
        assertEquals(Story.TooLittle(0, 0), nothing)
    }

    @Test
    fun `a full year gives the cards in order, ending with the shareable summary`() {
        val story = WrappedBuilder.build(summary(plays(40, 4))) as Story.Ready
        val kinds = story.cards.map { it::class.simpleName }
        assertEquals("Intro", kinds.first())
        assertEquals("Final", kinds.last())
        assertTrue(kinds.containsAll(listOf("Minutes", "TopArtists", "TopSongs", "TopAlbums", "TopDay", "LongestStreak", "PersonalityLine")))
        assertFalse("no genre known: no genre card", kinds.contains("TopGenres"))
        val minutes = story.cards.filterIsInstance<Card.Minutes>().single()
        assertEquals(160L, minutes.minutes)
        assertEquals(40, minutes.plays)
        assertEquals(5, story.cards.filterIsInstance<Card.TopSongs>().single().entries.size)
        val final = story.cards.last() as Card.Final
        assertEquals(160L, final.minutes)
        assertTrue(final.topArtist!!.startsWith("Artist"))
        assertNull(final.topGenre)
    }

    @Test
    fun `the personality line comes from the user's own hours and artists`() {
        assertEquals(Personality.NIGHT_OWL, WrappedBuilder.personality(summary(plays(20, 5, hour = 23))))
        assertEquals(Personality.EARLY_BIRD, WrappedBuilder.personality(summary(plays(20, 5, hour = 7))))
        assertEquals(Personality.LOYAL_FAN, WrappedBuilder.personality(summary(plays(20, 5, songCount = 1), songCount = 1)))
        assertEquals(Personality.ALL_ROUNDER, WrappedBuilder.personality(summary(plays(20, 5))))
        val marathon = (0 until 10).map { Play("s${it % 6 + 1}", LocalDateTime.of(2026, 5, 1 + it % 2, 15, 0), 60 * min) }
        assertEquals(Personality.MARATHON, WrappedBuilder.personality(summary(marathon)))
        assertEquals(Personality.ALL_ROUNDER, WrappedBuilder.personality(summary(emptyList())))
    }

    @Test
    fun `Inicio offers the card in December and early January, never a hidden year`() {
        assertEquals(2026, WrappedBuilder.homeCardYear(LocalDate.of(2026, 12, 1), null))
        assertEquals(2026, WrappedBuilder.homeCardYear(LocalDate.of(2027, 1, 7), null))
        assertNull(WrappedBuilder.homeCardYear(LocalDate.of(2027, 1, 8), null))
        assertNull(WrappedBuilder.homeCardYear(LocalDate.of(2026, 11, 30), null))
        assertNull("hidden this year", WrappedBuilder.homeCardYear(LocalDate.of(2026, 12, 20), 2026))
        assertEquals("comes back next December", 2027, WrappedBuilder.homeCardYear(LocalDate.of(2027, 12, 2), 2026))
        assertEquals(2026, WrappedBuilder.wrappedYear(LocalDate.of(2027, 1, 3)))
        assertEquals(2026, WrappedBuilder.wrappedYear(LocalDate.of(2026, 11, 27)))
        assertEquals(YearMonth.of(2026, 10), WrappedBuilder.wrappedMonth(LocalDate.of(2026, 11, 2)))
        assertEquals(YearMonth.of(2026, 11), WrappedBuilder.wrappedMonth(LocalDate.of(2026, 11, 27)))
    }

    @Test
    fun `Inicio offers the card only when the screen would show the summary`() {
        assertFalse("25 synced listens with 12 minutes are too little", WrappedBuilder.hasEnough(plays = 25, minutes = 12))
        assertFalse(WrappedBuilder.hasEnough(plays = 9, minutes = 600))
        assertTrue(WrappedBuilder.hasEnough(plays = 10, minutes = 30))
        val short = summary(plays(12, 1))
        assertEquals(WrappedBuilder.hasEnough(short), WrappedBuilder.hasEnough(short.plays, short.minutes))
        val card = main("ui/newui/AuraWrappedScreen.kt").substringAfter("internal fun AuraWrappedHomeCard(")
            .substringBefore("internal fun AuraStatsWrappedEntries(")
        assertTrue(card.contains("WrappedBuilder.hasEnough(plays, database.statsPlayTimeInRange(range.startStored, range.endStored) / 60_000L)"))
        assertTrue(card.contains("if (!enough) return"))
        val dao = main("db/DatabaseDao.kt")
        assertTrue(dao.contains("@Query(\"SELECT COALESCE(SUM(MAX(playTime, 0)), 0) FROM event WHERE timestamp >= :fromTimeStamp AND timestamp < :toTimeStamp\")\n    suspend fun statsPlayTimeInRange("))
    }

    @Test
    fun `the route argument names a year or a month and nothing else`() {
        assertEquals("2026", WrappedBuilder.yearArg(2026))
        assertEquals("2026-03", WrappedBuilder.monthArg(YearMonth.of(2026, 3)))
        val y = WrappedBuilder.parse("2026")!!
        assertEquals(WrappedBuilder.Kind.YEAR, y.kind)
        assertEquals(ListeningStats.year(2026), y.range)
        val m = WrappedBuilder.parse("2026-02")!!
        assertEquals(WrappedBuilder.Kind.MONTH, m.kind)
        assertEquals(ListeningStats.month(YearMonth.of(2026, 2)), m.range)
        for (bad in listOf(null, "", "26", "2026-13", "2026-1", "x2026", "2026/02")) assertNull(bad, WrappedBuilder.parse(bad))
    }

    // ── Nothing leaves the phone ──────────────────────────────────────────────────────────────────────────────────────

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private fun code(src: String) = src.lines().filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
        .joinToString("\n") { it.substringBefore("// ") }

    @Test
    fun `the statistics and Wrapped code never reaches the network, an AI or a crash reporter`() {
        val files = listOf(
            "stats/ListeningStats.kt", "stats/StatsDedupe.kt", "stats/StatsRows.kt", "stats/StatsFormat.kt", "stats/WrappedBuilder.kt",
            "viewmodels/WrappedViewModel.kt", "ui/newui/AuraWrappedScreen.kt", "ui/newui/AuraStatsHabits.kt", "utils/WrappedImage.kt",
        )
        val forbidden = listOf(
            "YouTube", "innertube", "OkHttp", "okhttp3", "HttpURLConnection", "java.net.URL", "URL(", "ktor", "HttpClient",
            "Retrofit", "AiPlaylist", "GenreCache.enrich", "iTunes", "Firebase", "CrashReporter", "reportException",
            "DeviceSync", "LibrarySync", "AccountPush", "WorkManager", "ImageLoader", "AsyncImage", "EXTRA_TEXT",
        )
        for (f in files) {
            val src = code(main(f))
            for (word in forbidden) assertFalse("$f uses $word", src.contains(word))
        }
        val screen = code(main("ui/newui/AuraWrappedScreen.kt"))
        assertEquals("one way out: the share sheet, with the image only", 1, Regex("""Intent\.ACTION_SEND\b""").findAll(screen).count())
        assertTrue(screen.contains("putExtra(Intent.EXTRA_STREAM, uri)"))
        assertTrue("only after the user taps the share button", screen.contains("ActionLog.tap(ActionId.WRAPPED_SHARE)"))
        assertTrue("the image stays in the private cache", screen.contains("WrappedImage.saveForShare(context, image)"))
        assertFalse("never the public gallery (MediaStore)", screen.contains("saveBitmapAsFile("))
        val image = code(main("utils/WrappedImage.kt"))
        assertTrue(image.contains("val dir = File(context.cacheDir, \"images\").apply { mkdirs() }"))
        assertTrue("one fixed file, overwritten", image.contains("private const val SHARE_FILE = \"aura_wrapped.png\""))
        assertTrue(image.contains("FileProvider.getUriForFile(context, \"\${context.packageName}.FileProvider\", file)"))
        assertFalse(image.contains("MediaStore"))
    }

    @Test
    fun `the screen is reachable, battery-safe and works with the remote`() {
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("route = \"wrapped/{period}\","))
        assertTrue(nav.contains("iad1tya.echo.music.ui.newui.AuraWrappedScreen(navController)"))
        val stats = main("ui/newui/AuraStatsScreen.kt")
        assertTrue(stats.contains("AuraStatsWrappedEntries(navController = navController, modifier = Modifier.animateItem())"))
        val home = main("ui/newui/AuraHomeScreen.kt")
        assertTrue(home.contains("item(key = \"aura_home_wrapped\", contentType = \"aura_home_wrapped\") {"))
        val quick = home.indexOf("key = \"aura_home_quick_access\"")
        val wrapped = home.indexOf("key = \"aura_home_wrapped\"")
        val chips = home.indexOf("key = \"aura_home_chips\"")
        assertTrue("after the four tiles, before the mood chips", quick in 1 until wrapped && wrapped < chips)

        val screen = main("ui/newui/AuraWrappedScreen.kt")
        assertFalse("no endless animation", Regex("""rememberInfiniteTransition|withFrameNanos|while \(true\)|delay\(""").containsMatchIn(screen))
        assertTrue("the card animates only when it settles", screen.contains("val settled = pagerState.settledPage == page"))
        assertTrue("reduced motion: no animation", screen.contains("if (reducedMotion) snap() else tween(durationMillis = 420)"))
        assertTrue("D-pad right", screen.contains("Key.DirectionRight ->"))
        assertTrue("D-pad left", screen.contains("Key.DirectionLeft ->"))
        assertTrue("OK on the remote", screen.contains("event.key == Key.DirectionCenter || event.key == Key.Enter"))
        assertTrue("tap left third goes back", screen.contains("if (offset.x < size.width / 3f) {"))
        assertTrue("the Home card asks the database only when it can show", screen.contains("WrappedBuilder.homeCardYear(today, dismissedYear.takeIf { it != 0 }) ?: return"))
    }
}
