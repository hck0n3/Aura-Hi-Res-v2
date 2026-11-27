package iad1tya.echo.music.stats

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 909 — «Tus hábitos» on Estadísticas reads the phone's own `event` table, recomputes only when a listen is
 * added or removed (never on a timer), stops while the screen is gone, and the Resumen counts are the de-duplicated ones.
 */
class StatsInsightsWiringGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the insights are event driven and stop with the screen`() {
        val vm = main("viewmodels/StatsViewModel.kt")
        assertTrue(vm.contains("combine(selectedOption, indexChips, database.statsEventCount())"))
        assertTrue(vm.contains(".stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)"))
        assertTrue("computed off the main thread", vm.contains("withContext(Dispatchers.IO) {"))
        assertTrue("genres only from what the phone already knows", vm.contains("GenreCache.snapshot(context)"))
        assertFalse("never fetches genres for the statistics", vm.contains("GenreCache.enrich("))
        val insights = vm.substringAfter("val insights:").substringBefore("    val firstEvent =")
        assertFalse("a failure is not reported off the phone", insights.contains("reportException("))
        assertFalse("no polling", Regex("""\bdelay\(|while \(true\)""").containsMatchIn(insights))
    }

    @Test
    fun `the queries only read`() {
        val dao = main("db/DatabaseDao.kt")
        val block = dao.substringAfter("fun statsEventCount(): Flow<Int>").substringBefore("suspend fun statsArtistFirstPlays()")
        assertTrue(dao.contains("@Query(\"SELECT COUNT(*) FROM event\")\n    fun statsEventCount(): Flow<Int>"))
        for (verb in listOf("INSERT", "UPDATE", "DELETE")) assertFalse(verb, block.contains(verb))
        assertTrue(dao.contains("WHERE timestamp >= :fromTimeStamp AND timestamp < :toTimeStamp\")\n    suspend fun statsPlaysInRange("))
    }

    @Test
    fun `the screen shows the habits and the de-duplicated Resumen counts`() {
        val screen = main("ui/newui/AuraStatsScreen.kt")
        assertTrue(screen.contains("item(key = \"aura_stats_habits\", contentType = \"aura_stats_habits\") {"))
        assertTrue(screen.contains("AuraStatsHabits(summary = summary, modifier = Modifier.animateItem())"))
        assertTrue(screen.contains("uniqueSongs = insights?.songs ?: uniqueSongsCount,"))
        assertTrue(screen.contains("uniqueArtists = insights?.artists ?: uniqueArtistsCount,"))
        assertTrue(screen.contains("uniqueAlbums = insights?.albums ?: uniqueAlbumsCount,"))
        val habits = main("ui/newui/AuraStatsHabits.kt")
        assertFalse("static charts: no animation while music plays", Regex("""rememberInfiniteTransition|animate\w*AsState|withFrameNanos""").containsMatchIn(habits))
    }
}
