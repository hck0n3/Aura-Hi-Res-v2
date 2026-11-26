package iad1tya.echo.music.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 894 — one library for the three sources: the best copy of a song plays first, the artist page shows
 * «En tu móvil» and «En tu nube» apart, and the Library search finds cloud songs.
 */
class CloudOneLibraryTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private val phone = "content://media/external/audio/media/7"
    private val cloud = CloudSongIds.of("aura-onedrive", "Music/a.flac")
    private val online = "dQw4w9WgXcQ"

    private fun c(id: String, title: String = "Song", artist: String = "Artist", seconds: Int = 200, lossless: Boolean = false) =
        BestSourcePolicy.Candidate(id, title, listOf(artist), seconds, lossless)

    @Test
    fun `the source comes from the id`() {
        assertEquals(BestSourcePolicy.Source.PHONE, BestSourcePolicy.sourceOf(phone))
        assertEquals(BestSourcePolicy.Source.PHONE, BestSourcePolicy.sourceOf("file:///sdcard/a.mp3"))
        assertEquals(BestSourcePolicy.Source.CLOUD, BestSourcePolicy.sourceOf(cloud))
        assertEquals(BestSourcePolicy.Source.ONLINE, BestSourcePolicy.sourceOf(online))
    }

    @Test
    fun `phone lossless, then cloud, then online - a lossless copy beats a compressed one, the phone breaks ties`() {
        assertEquals(mapOf(cloud to phone, online to phone), BestSourcePolicy.bestTwins(listOf(c(online), c(cloud), c(phone, lossless = true))))
        assertEquals(mapOf(online to cloud), BestSourcePolicy.bestTwins(listOf(c(online), c(cloud))))
        assertEquals("a lossless cloud copy beats a compressed phone copy", mapOf(phone to cloud), BestSourcePolicy.bestTwins(listOf(c(phone), c(cloud, lossless = true))))
        assertEquals("same quality: the phone, no data", mapOf(cloud to phone), BestSourcePolicy.bestTwins(listOf(c(cloud), c(phone))))
        val ranks = listOf(c(phone, lossless = true), c(cloud, lossless = true), c(phone), c(cloud), c(online)).map(BestSourcePolicy::rank)
        assertEquals(ranks.sorted(), ranks)
        assertEquals(5, ranks.toSet().size)
    }

    @Test
    fun `the same song - normalised title, a shared artist, durations two seconds apart at most`() {
        assertTrue(BestSourcePolicy.sameSong(c(phone, "Song (Remastered 2011)"), c(cloud, "song")))
        assertTrue(BestSourcePolicy.sameSong(c(phone, seconds = 200), c(cloud, seconds = 202)))
        assertFalse(BestSourcePolicy.sameSong(c(phone, seconds = 200), c(cloud, seconds = 203)))
        assertFalse("another recording", BestSourcePolicy.sameSong(c(phone, "Song (Live)"), c(cloud, "Song")))
        assertFalse("another artist", BestSourcePolicy.sameSong(c(phone, artist = "A"), c(cloud, artist = "B")))
        assertTrue("one artist in common is enough", BestSourcePolicy.sameSong(c(phone), BestSourcePolicy.Candidate(cloud, "Song", listOf("Other", "artist"), 200, false)))
        assertFalse("an unknown duration never matches", BestSourcePolicy.sameSong(c(phone, seconds = 0), c(cloud, seconds = 0)))
        assertEquals("a single copy has no twin", emptyMap<String, String>(), BestSourcePolicy.bestTwins(listOf(c(phone), c(cloud, "Other song"))))
        assertEquals(2, BestSourcePolicy.DURATION_TOLERANCE_SECONDS)
    }

    @Test
    fun `two copies on the same source are never twins - each plays itself`() {
        val online2 = "abcdefghijk"
        val phone2 = "content://media/external/audio/media/8"
        val cloud2 = CloudSongIds.of("aura-onedrive", "Music/b.flac")
        val none = emptyMap<String, String>()
        assertEquals("explicit and clean online", none, BestSourcePolicy.bestTwins(listOf(c(online), c(online2))))
        assertEquals("two files on the phone", none, BestSourcePolicy.bestTwins(listOf(c(phone2), c(phone, lossless = true))))
        assertEquals("two files in the cloud", none, BestSourcePolicy.bestTwins(listOf(c(cloud), c(cloud2, lossless = true))))
        // With another source present, only the copies on another source than the best one are mapped.
        assertEquals(
            mapOf(cloud to phone),
            BestSourcePolicy.bestTwins(listOf(c(phone2), c(cloud), c(phone, lossless = true))),
        )
    }

    @Test
    fun `the artist page lists both sources apart and plays one copy per song, the best`() {
        val screen = main("ui/newui/AuraArtistScreen.kt")
        assertTrue(screen.contains("val bestTwinOf = remember(allLibrarySongs) { BestSourcePolicy.bestTwins(allLibrarySongs.map(::bestSourceCandidate)) }"))
        // Split by source, never «not cloud = phone»: an online song of the library is never under «En tu móvil».
        assertFalse(screen.contains("filteredLibrarySongs.partition { it.id.isCloudSongId() }"))
        assertTrue(screen.contains("val bySource = filteredLibrarySongs.groupBy { BestSourcePolicy.sourceOf(it.id) }"))
        assertTrue(screen.contains("val phoneSongs = bySource[BestSourcePolicy.Source.PHONE].orEmpty()"))
        assertTrue(screen.contains("val songSections = if (cloudSongs.isNotEmpty() && phoneSongs.isNotEmpty()) {"))
        assertTrue(screen.contains("Triple(\"_phone\", R.string.artist_source_phone, phoneSongs),\n                            Triple(\"_cloud\", R.string.artist_source_cloud, cloudSongs),"))
        assertTrue("online songs keep «Canciones»", screen.contains("onlineSongs.takeIf { it.isNotEmpty() }?.let { Triple(\"_online\", R.string.songs, it) },"))
        assertTrue("one source: the «Canciones» of before", screen.contains("listOf(Triple(\"\", R.string.songs, filteredLibrarySongs))"))
        assertTrue("every copy stays listed", screen.contains("itemCount = sectionSongs.size,"))
        assertTrue(screen.contains("allSongs = playQueue,"))
        assertTrue(screen.contains("playId = bestTwinOf[song.id]?.takeIf(filteredIds::contains) ?: song.id,"))
        assertTrue(screen.contains("startIndex = allSongs.indexOfFirst { it.id == playId }.coerceAtLeast(0),"))
        assertTrue("the shuffle too", screen.contains("val playable = allLibrarySongs.filter { it.id !in bestTwinOf }"))
        for ((dir, phoneText, cloudText) in listOf(Triple("values", "On your phone", "In your cloud"), Triple("values-es", "En tu móvil", "En tu nube"))) {
            val xml = File(root, "app/src/main/res/$dir/strings.xml").readText()
            assertTrue(xml.contains("<string name=\"artist_source_phone\">$phoneText</string>"))
            assertTrue(xml.contains("<string name=\"artist_source_cloud\">$cloudText</string>"))
        }
    }

    @Test
    fun `the Library search finds cloud songs, their albums and artists`() {
        // Cloud songs are isLocal = 1 rows (row 891): the three search queries already take them (row 883).
        val dao = main("db/DatabaseDao.kt")
        assertTrue(dao.contains("FROM song WHERE (inLibrary IS NOT NULL OR liked = 1 OR isLocal = 1) AND title LIKE '%' || :query || '%'"))
        assertTrue(dao.contains("WHERE artistId = artist.id AND (song.inLibrary IS NOT NULL OR song.liked = 1 OR song.isLocal = 1)) AS songCount FROM artist"))
        assertTrue(dao.contains("EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND (song.inLibrary IS NOT NULL OR song.isLocal = 1))"))
        val search = main("viewmodels/LocalSearchViewModel.kt")
        for (q in listOf("database.searchSongs(query, PREVIEW_SIZE)", "database.searchAlbums(query, PREVIEW_SIZE)", "database.searchArtists(query, PREVIEW_SIZE)")) {
            assertTrue(q, search.contains(q))
        }
        val indexer = main("cloud/CloudLibraryIndexer.kt")
        assertTrue(indexer.contains("isLocal = true,"))
    }
}
