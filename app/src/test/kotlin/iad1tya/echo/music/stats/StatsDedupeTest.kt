package iad1tya.echo.music.stats

import iad1tya.echo.music.cloud.CloudSongIds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 909 — in Estadísticas one song, artist or album counts once across the phone, the cloud and online.
 */
class StatsDedupeTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private val phone = "content://media/external/audio/media/7"
    private val cloud = CloudSongIds.of("aura-onedrive", "Music/a.flac")
    private val online = "dQw4w9WgXcQ"

    private fun key(id: String, title: String = "Song", artist: String = "Artist", seconds: Int = 200) =
        StatsDedupe.SongKey(id, title, listOf(artist), seconds)

    private data class Row(val id: String, val plays: Int, val ms: Long)

    @Test
    fun `the same song on three sources is one group, led by the first listed`() {
        val groups = StatsDedupe.songGroups(listOf(key(online, "Song (Official Video)"), key(phone), key(cloud, seconds = 201)))
        assertEquals(mapOf(online to online, phone to online, cloud to online), groups)
    }

    @Test
    fun `two copies on the same source stay two songs, and so do different songs`() {
        val twoOnline = StatsDedupe.songGroups(listOf(key(online), key("aBcDeFgHiJk")))
        assertEquals(mapOf(online to online, "aBcDeFgHiJk" to "aBcDeFgHiJk"), twoOnline)
        val live = StatsDedupe.songGroups(listOf(key(online), key(phone, title = "Song (Live)")))
        assertEquals(phone, live[phone])
        val otherArtist = StatsDedupe.songGroups(listOf(key(online), key(phone, artist = "Someone else")))
        assertEquals(phone, otherArtist[phone])
        val longer = StatsDedupe.songGroups(listOf(key(online), key(phone, seconds = 260)))
        assertEquals(phone, longer[phone])
        val unknownLength = StatsDedupe.songGroups(listOf(key(online), key(cloud, seconds = -1)))
        assertEquals("a cloud song with no tags read is never paired", cloud, unknownLength[cloud])
    }

    @Test
    fun `merged rows add their plays and time and re-sort by time`() {
        val rows = listOf(Row("a", 3, 900), Row("b", 4, 600), Row("a2", 2, 500))
        val groups = mapOf("a2" to "b")
        val merged = StatsDedupe.mergeGroups(
            items = rows,
            keyOf = { groups[it.id] ?: it.id },
            weight = { it.ms },
            merge = { into, other -> into.copy(plays = into.plays + other.plays, ms = into.ms + other.ms) },
        )
        assertEquals(listOf(Row("b", 6, 1100), Row("a", 3, 900)), merged)
    }

    @Test
    fun `the preferred row survives a merge`() {
        val rows = listOf(Row("LOCAL_ARTIST_x", 5, 700), Row("UCabc", 1, 100))
        val merged = StatsDedupe.mergeGroups(
            items = rows,
            keyOf = { "same" },
            weight = { it.ms },
            preferred = { it.id.startsWith("UC") },
            merge = { into, other -> into.copy(plays = into.plays + other.plays, ms = into.ms + other.ms) },
        )
        assertEquals(listOf(Row("UCabc", 6, 800)), merged)
    }

    @Test
    fun `artists match by normalised name and albums need a title and an artist`() {
        assertEquals(StatsDedupe.artistKey("LOCAL_ARTIST_1", "Beyoncé"), StatsDedupe.artistKey("UC1", "beyonce - Topic"))
        assertFalse(StatsDedupe.artistKey("a", "") == StatsDedupe.artistKey("b", ""))
        assertEquals(
            StatsDedupe.albumKey("LA1", "Abbey Road (Remastered)", "The Beatles"),
            StatsDedupe.albumKey("MPREb_1", "Abbey Road", "the beatles"),
        )
        assertFalse(
            "two «Greatest Hits» with no artist are not one album",
            StatsDedupe.albumKey("x", "Greatest Hits", null) == StatsDedupe.albumKey("y", "Greatest Hits", null),
        )
    }

    @Test
    fun `the view model folds the shelves and the screen pairs songs by id and plays local songs locally`() {
        val vm = main("viewmodels/StatsViewModel.kt")
        assertFalse("local and cloud artists are listed too", vm.contains("artists.filter { it.artist.isYouTubeArtist }"))
        assertTrue("only YouTube artists are refreshed from YouTube", vm.contains(".filter { it.isYouTubeArtist }"))
        assertTrue(vm.contains("StatsDedupe.songGroups("))
        assertTrue(vm.contains("keyOf = { StatsDedupe.artistKey(it.id, it.artist.name) },"))
        assertTrue(vm.contains("keyOf = { StatsDedupe.albumKey(it.id, it.album.title, it.artists.firstOrNull()?.name) },"))
        val screen = main("ui/newui/AuraStatsScreen.kt")
        assertTrue(screen.contains("val original = songsById[song.id]"))
        assertFalse(screen.contains("mostPlayedSongs.getOrNull(index)"))
        assertTrue(screen.contains("BestSourcePolicy.sourceOf(song.id) != BestSourcePolicy.Source.ONLINE"))
    }
}
