package iad1tya.echo.music.cloud

import iad1tya.echo.music.localmedia.LocalArtistSplitRules
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 891 — the cloud library: folders, incremental index, ids, tags, and the data-safety rules. The pure parts
 * run on the JVM; the Android side (MediaMetadataRetriever, Room, the daemon) is pinned on the source text.
 */
class CloudLibraryIndexTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private fun entry(path: String, size: Long = 100, mod: Long? = 1_000L, dir: Boolean = false) =
        CloudEntry(name = path.substringAfterLast('/'), path = path, size = size, mimeType = null, modTimeMs = mod, isDir = dir)

    private fun record(remote: String, path: String, size: Long = 100, mod: Long? = 1_000L, tags: CloudTags? = null) =
        CloudFileRecord(CloudSongIds.of(remote, path), remote, path, size, mod, tags, tags?.let { CloudIndexPolicy.fingerprint(size, mod) })

    @Test
    fun `a cloud song id is stable, per remote and path, and never reveals the path`() {
        val id = CloudSongIds.of("aura-onedrive", "Música/Queen/01 Bohemian Rhapsody.flac")
        assertEquals(id, CloudSongIds.of("aura-onedrive", "Música/Queen/01 Bohemian Rhapsody.flac"))
        assertTrue(Regex("""aura-cloud://aura-onedrive/[0-9a-f]{32}""").matches(id))
        assertTrue(id != CloudSongIds.of("aura-dropbox", "Música/Queen/01 Bohemian Rhapsody.flac"))
        assertTrue(id != CloudSongIds.of("aura-onedrive", "Música/Queen/01 Bohemian Rhapsody (1).flac"))
        for (part in listOf("Queen", "Bohemian", "Música", "flac")) assertFalse(part, id.contains(part))
        assertTrue(id.isCloudSongId())
        assertEquals("aura-onedrive", CloudSongIds.remoteOf(id))
        assertFalse("content://media/external/audio/media/12".isCloudSongId())
        assertFalse("dQw4w9WgXcQ".isCloudSongId())
        assertNull(CloudSongIds.remoteOf("dQw4w9WgXcQ"))
    }

    @Test
    fun `only the audio Biblioteca plays is taken`() {
        for (name in listOf("a.mp3", "b.FLAC", "c.m4a", "d.aac", "e.ogg", "f.opus", "g.wav", "h.mka", "i.oga", "j.m4b")) {
            assertTrue(name, CloudAudioFiles.isAudio(name))
        }
        for (name in listOf("cover.jpg", "notes.txt", "clip.mp4", "rip.cue", "a.ape", "a.dsf", "noext", "a.wv")) {
            assertFalse(name, CloudAudioFiles.isAudio(name))
        }
        assertEquals("audio/flac", CloudAudioFiles.mimeOf("x.flac"))
        assertTrue(CloudAudioFiles.isLosslessMime("audio/flac"))
        assertTrue(CloudAudioFiles.isLosslessMime("audio/wav"))
        assertFalse(CloudAudioFiles.isLosslessMime("audio/mpeg"))
        assertFalse(CloudAudioFiles.isLosslessMime(null))
        // Every mime type the index stores is one Biblioteca ▸ Local and Android Auto accept (SupportedLocalAudio).
        val supported = main("localmedia/SupportedLocalAudio.kt")
        for (ext in listOf("mp3", "flac", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "mka")) {
            assertTrue(ext, supported.contains("\"${CloudAudioFiles.mimeOf("x.$ext")}\""))
        }
    }

    @Test
    fun `the index is incremental - an unchanged file keeps its tags, a changed or unread one is read again`() {
        val tags = CloudTags(title = "T")
        val read = record("r", "a.flac", size = 10, mod = 5, tags = tags)
        assertFalse(CloudIndexPolicy.needsTagRead(read, 10, 5))
        assertTrue("size changed", CloudIndexPolicy.needsTagRead(read, 11, 5))
        assertTrue("date changed", CloudIndexPolicy.needsTagRead(read, 10, 6))
        assertTrue("new", CloudIndexPolicy.needsTagRead(null, 10, 5))
        assertTrue("never read", CloudIndexPolicy.needsTagRead(read.copy(tags = null), 10, 5))
        assertFalse("no date from the cloud is a date too", CloudIndexPolicy.needsTagRead(record("r", "b.mp3", 3, null, tags), 3, null))
        // Stale: never indexed, a day old, or a clock that went back.
        val day = CloudIndexPolicy.STALE_AFTER_MS
        assertTrue(CloudIndexPolicy.isStale(0L, 5L))
        assertFalse(CloudIndexPolicy.isStale(1_000L, 1_000L + day - 1))
        assertTrue(CloudIndexPolicy.isStale(1_000L, 1_000L + day))
        assertTrue(CloudIndexPolicy.isStale(10_000L, 5_000L))
        assertEquals(200, CloudIndexPolicy.MAX_TAG_READS_PER_RUN)
    }

    @Test
    fun `a failed or partial listing never removes a song, and other clouds are untouched`() {
        val other = record("dropbox", "Music/x.mp3")
        val inComplete = record("onedrive", "Music/Rock/a.mp3")
        val inBroken = record("onedrive", "Albums/b.mp3")
        val unchosen = record("onedrive", "Old/c.mp3")
        val seenAgain = record("onedrive", "Music/Rock/d.mp3")
        val merge = CloudIndexPolicy.merge(
            previous = listOf(other, inComplete, inBroken, unchosen, seenAgain),
            remote = "onedrive",
            chosen = listOf("Music", "Albums"),
            completeFolders = setOf("Music"),
            seen = listOf(seenAgain.copy(size = 999)),
        )
        assertEquals(listOf(inComplete, unchosen), merge.gone)
        assertEquals(setOf(other.id, inBroken.id, seenAgain.id), merge.records.map { it.id }.toSet())
        assertEquals(999L, merge.records.first { it.id == seenAgain.id }.size)
    }

    @Test
    fun `the walk is bounded, cancellable, and a failed folder leaves its root incomplete`() {
        val tree = mapOf(
            "" to listOf(entry("Music", dir = true), entry("Other", dir = true), entry("top.mp3")),
            "Music" to listOf(entry("Music/a.flac"), entry("Music/cover.jpg"), entry("Music/Sub", dir = true)),
            "Music/Sub" to listOf(entry("Music/Sub/b.mp3")),
            "Other" to listOf(entry("Other/c.mp3")),
            "Broken" to emptyList(),
        )
        val list: (String) -> List<CloudEntry> = { dir -> if (dir == "Broken") error("listing failed") else tree.getValue(dir) }
        val all = CloudFolderWalk.walk(listOf("Music", "Music/Sub", "Broken"), list)
        assertEquals(listOf("Music/a.flac", "Music/Sub/b.mp3"), all.files.map { it.path })
        assertEquals(setOf("Music"), all.completeFolders)
        assertEquals(1, all.failedListings)
        val shallow = CloudFolderWalk.walk(listOf("Music"), list, maxDepth = 0)
        assertEquals(listOf("Music/a.flac"), shallow.files.map { it.path })
        assertTrue("a folder below the depth bound leaves it incomplete", shallow.completeFolders.isEmpty())
        val few = CloudFolderWalk.walk(listOf(""), list, maxFolders = 1)
        assertTrue(few.completeFolders.isEmpty())
        assertEquals(1, few.foldersListed)
        val capped = CloudFolderWalk.walk(listOf(""), list, maxFiles = 2)
        assertEquals(2, capped.files.size)
        assertTrue(capped.completeFolders.isEmpty())
        var calls = 0
        try {
            CloudFolderWalk.walk(listOf(""), list, checkActive = { if (++calls > 1) throw java.util.concurrent.CancellationException() })
            throw AssertionError("must be cancelled")
        } catch (_: java.util.concurrent.CancellationException) {
        }
        assertEquals(listOf(""), CloudIndexPolicy.outermost(listOf("Music", "", "Music/Sub")))
        assertEquals(listOf("Music"), CloudIndexPolicy.outermost(listOf("Music/Sub", "/Music/", "Music")))
        assertEquals(listOf("A", "B"), CloudIndexPolicy.outermost(listOf("B", "A", "A/x")))
    }

    @Test
    fun `the title falls back to the file name only when the file has no title tag`() {
        assertEquals("Real Title", CloudTagFallback.title(CloudTags(title = " Real Title "), "01 file.flac"))
        assertEquals("01 file", CloudTagFallback.title(CloudTags(), "01 file.flac"))
        assertEquals("01 file", CloudTagFallback.title(null, "01 file.flac"))
        assertEquals(".flac", CloudTagFallback.title(null, ".flac"))
        assertEquals(215, CloudTagFallback.durationSeconds(CloudTags(durationMs = 215_900)))
        assertEquals(0, CloudTagFallback.durationSeconds(null))
    }

    @Test
    fun `a cloud song gets the phone scanner's artist and album rules and ids`() {
        val rules = LocalArtistSplitRules.Default
        val r = record(
            "onedrive", "Q/02 b.flac",
            tags = CloudTags(title = "B", artist = "Queen; David Bowie", albumArtist = "Queen", album = "Hot Space", year = 1982, disc = 1, track = 2, durationMs = 240_000),
        )
        val t = CloudLibraryTracks.track(r, rules, "Unknown")
        assertEquals(listOf("Queen", "David Bowie"), t.artists.map { it.name })
        assertEquals(t.artists.first().id, CloudLibraryTracks.track(record("x", "y.mp3", tags = CloudTags(artist = "queen ")), rules, "U").artists.single().id)
        val expectedAlbum = "LOCAL_ALBUM_" + UUID.nameUUIDFromBytes("Hot Space|${t.albumArtists.single().id}".toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")
        assertEquals("same id as a phone file without a MediaStore album (LocalSongScanner.buildAlbumId)", expectedAlbum, t.albumId)
        assertTrue(main("localmedia/LocalSongScanner.kt").contains("\"LOCAL_ALBUM_\${stableHash(\"\$albumName|\$primaryArtistId\")}\""))
        assertEquals("audio/flac", t.mimeType)
        assertEquals(240, t.durationSeconds)
        val untagged = CloudLibraryTracks.track(record("onedrive", "Q/03 c.mp3"), rules, "Unknown")
        assertEquals("03 c", untagged.title)
        assertEquals("Unknown", untagged.artists.single().name)
        assertNull(untagged.albumId)
        // Disc then track then title inside an album.
        val tracks = listOf(
            t.copy(id = "a", disc = 2, track = 1), t.copy(id = "b", disc = 1, track = 3), t.copy(id = "c", disc = 1, track = 1),
        )
        assertEquals(mapOf("c" to 0, "b" to 1, "a" to 2), CloudLibraryTracks.albumIndexes(tracks))
        val album = CloudLibraryTracks.albums(tracks).single()
        assertEquals(3, album.songCount)
        assertEquals(listOf(t.albumArtists.single().id), album.artistIds)
    }

    @Test
    fun `the index file round-trips and an unreadable one is an empty library`() {
        val state = CloudLibraryState(
            folders = mapOf("aura-onedrive" to listOf("Music", ""), "aura-dropbox" to emptyList()),
            files = listOf(
                record("aura-onedrive", "Music/a.flac", tags = CloudTags("T", "A", null, "Al", 2001, 1, 3, 1234)),
                record("aura-onedrive", "Music/b.mp3", mod = null),
                record("aura-onedrive", "Music/c.mp3", tags = CloudTags()),
            ),
            lastIndexAtMs = 42L,
        )
        assertEquals(state, CloudLibraryCodec.decode(CloudLibraryCodec.encode(state)))
        assertEquals(CloudLibraryState(), CloudLibraryCodec.decode(null))
        assertEquals(CloudLibraryState(), CloudLibraryCodec.decode("{not json"))
        assertEquals(CloudLibraryState(), CloudLibraryCodec.decode("""{"v":99,"files":[]}"""))
    }

    @Test
    fun `a path becomes a percent-encoded URL path`() {
        assertEquals("Sub%20Dir/a%20b%23%3F%25.flac", CloudPaths.encode("Sub Dir/a b#?%.flac"))
        assertEquals("M%C3%BAsica/01-x_y.z~.mp3", CloudPaths.encode("Música/01-x_y.z~.mp3"))
    }

    @Test
    fun `indexing runs only when asked, after choosing folders, or once per app start when stale on Wi-Fi`() {
        val indexer = main("cloud/CloudLibraryIndexer.kt")
        val auto = indexer.substringAfter("suspend fun indexIfStale() {").substringBefore("\n    suspend fun index(")
        assertTrue("once per process", auto.contains("if (autoChecked) return\n        autoChecked = true"))
        assertTrue(auto.contains("if (state.folders.values.all { it.isEmpty() }) return"))
        assertTrue(auto.contains("if (!CloudIndexPolicy.isStale(state.lastIndexAtMs, System.currentTimeMillis())) return"))
        assertTrue("never on mobile data", auto.contains("if (metered != false) return"))
        assertTrue(auto.contains("index(Trigger.APP_START)"))
        for (bad in listOf("while (true)", "delay(", "repeat(", "Timer", "WorkManager", "PeriodicWork")) {
            assertFalse("no polling: $bad", indexer.contains(bad))
        }
        val autoScan = main("localmedia/LocalLibraryAutoScan.kt")
        assertTrue(autoScan.contains("scope.launch(Dispatchers.IO) { cloudIndexer.indexIfStale() }"))
        val vm = main("viewmodels/CloudAccountsViewModel.kt")
        assertTrue(vm.contains("index(CloudLibraryIndexer.Trigger.FOLDERS_CHOSEN)"))
        assertTrue(vm.contains("viewModelScope.launch { index(CloudLibraryIndexer.Trigger.USER) }"))
        assertEquals("the only callers", 2, Regex("""indexIfStale\(\)""").findAll(autoScan + indexer).count())
    }

    @Test
    fun `disconnecting, a failed listing or an automatic run never deletes a song`() {
        val indexer = main("cloud/CloudLibraryIndexer.kt")
        assertTrue("a disconnected cloud is skipped", indexer.contains("if (remote !in configured) {"))
        assertTrue("only clouds listed completely", indexer.contains("if (walk.completeFolders.size < chosen.size) complete = false else settled += remote"))
        assertTrue(indexer.contains("id.isCloudSongId() && id !in trackById && CloudSongIds.remoteOf(id) in settled"))
        assertTrue("row 881: a run nobody asked for never deletes", indexer.contains("mayDelete = trigger != Trigger.APP_START"))
        assertTrue(indexer.contains("val deletable = if (mayDelete) {"))
        assertTrue("used songs stay", indexer.contains("!it.song.liked && it.song.likedDate == null && it.song.totalPlayTime == 0L && !it.song.isDownloaded && it.song.dateDownload == null"))
        assertTrue(indexer.contains("localSongIdsInPlaylists(chunk) + localSongIdsWithEvents(chunk) + localSongIdsWithPlayCounts(chunk)"))
        assertTrue("row 882: moved files keep likes, lists and history", indexer.contains("val verdict = matchMovedLocalSongs(missing, appeared)"))
        for (move in listOf("moveLocalSongPlaylistEntries", "moveLocalSongEvents", "moveLocalSongPlayCounts", "moveLocalSongLyrics")) {
            assertTrue(move, indexer.contains("$move(oldId, newId)"))
        }
        // The disconnect itself only forgets the account; the songs stay.
        val disconnect = main("cloud/CloudRepository.kt").substringAfter("suspend fun disconnect(provider: CloudProvider) {").substringBefore("\n    }")
        assertTrue(disconnect.contains("RcRequests.deleteRemote(provider.remoteName)"))
        assertFalse(disconnect.contains("Song") || disconnect.contains("CloudLibraryStore"))
        // A phone scan never takes a cloud song for a missing phone file.
        assertTrue(main("localmedia/LocalSongScanner.kt").contains("val existingLocalIds = localSongIds().filterNot { it.isCloudSongId() }"))
        // The index lives next to the encrypted config, outside every backup.
        assertTrue(main("cloud/CloudLibraryStore.kt").contains("File(RcloneConfigFiles.dir(context).apply { mkdirs() }, FILE)"))
    }

    @Test
    fun `Local and Android Auto's Local folder stay the phone's files`() {
        assertTrue(main("viewmodels/LocalSongsViewModel.kt").contains(".map { list -> list.filterNot { it.id.isCloudSongId() } }"))
        assertTrue(main("playback/MediaLibrarySessionCallback.kt").contains("!iad1tya.echo.music.cloud.CloudSongIds.isCloud(song.id)"))
    }
}
