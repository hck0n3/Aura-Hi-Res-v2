package iad1tya.echo.music.localmedia

import java.io.File
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 881 (plan, phase 4: «reescaneo que actualiza… escaneo incremental»): a scan reopens only the files MediaStore
 * reports changed since the last successful scan, a full rescan stays one tap away, and scans nobody asked for (app
 * start, a MediaStore change) are event-driven and never delete.
 */
class LocalScanIncrementalTest {

    private val header = LocalScanCacheHeader(rules = LocalScanRulesVersion, shape = "S", mediaStoreVersion = "v1")

    @Test
    fun `a file is unchanged only when size, mtime and generation are all the same`() {
        val fp = LocalFileFingerprint(sizeBytes = 10, modifiedEpochSeconds = 100, generation = 7)
        assertTrue(isUnchangedFile(fp, fp.copy()))
        assertFalse(isUnchangedFile(null, fp))
        assertFalse(isUnchangedFile(fp, fp.copy(sizeBytes = 11)))
        assertFalse(isUnchangedFile(fp, fp.copy(modifiedEpochSeconds = 101)))
        assertFalse("a re-indexed file (tags edited) has a new generation", isUnchangedFile(fp, fp.copy(generation = 8)))
        assertFalse("Android 10 -> 11 adds a generation: read again once", isUnchangedFile(fp.copy(generation = null), fp))
        assertFalse("an unknown size is never trusted", isUnchangedFile(fp.copy(sizeBytes = 0), fp.copy(sizeBytes = 0)))
    }

    @Test
    fun `the remembered builds are dropped by a full rescan, new rules, a new shape or a rebuilt MediaStore`() {
        assertTrue(localScanCacheUsable(header, header.copy(), fullRescan = false, versionRequired = true))
        assertFalse(localScanCacheUsable(header, header, fullRescan = true, versionRequired = true))
        assertFalse(localScanCacheUsable(null, header, fullRescan = false, versionRequired = true))
        assertFalse(localScanCacheUsable(header.copy(rules = header.rules - 1), header, fullRescan = false, versionRequired = true))
        assertFalse(localScanCacheUsable(header.copy(shape = "old"), header, fullRescan = false, versionRequired = true))
        assertFalse(localScanCacheUsable(header.copy(mediaStoreVersion = "v0"), header, fullRescan = false, versionRequired = true))
        val unknown = header.copy(mediaStoreVersion = null)
        assertFalse("Android 10+: an unreadable MediaStore version reuses nothing", localScanCacheUsable(unknown, unknown, false, versionRequired = true))
        assertTrue("Android 9 has no MediaStore version", localScanCacheUsable(unknown, unknown, false, versionRequired = false))
    }

    @Test
    fun `a picked-folder file left out as too short is not reopened while unchanged, unless the minimum drops`() {
        val fp = LocalFileFingerprint(sizeBytes = 10, modifiedEpochSeconds = 100)
        val short = SkippedLocalFile(id = "doc", fingerprint = fp, durationMs = 12_000)
        assertTrue(skipsAgainUnopened(short, fp.copy(), minimumDurationMs = 30_000))
        assertFalse("the slider went down: read it again", skipsAgainUnopened(short, fp, minimumDurationMs = 10_000))
        assertFalse("the file changed: read it again", skipsAgainUnopened(short, fp.copy(modifiedEpochSeconds = 101), 30_000))
        assertFalse(skipsAgainUnopened(null, fp, 30_000))
        val unreadable = short.copy(durationMs = 0)
        assertTrue("no readable duration stays out until it changes or a full rescan", skipsAgainUnopened(unreadable, fp, 0))
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        val picked = scanner.substringAfter("private fun pickedFolderTracks(").substringBefore("private fun mediaStoreIndexedKeys(")
        val again = picked.indexOf("if (skippedBefore != null && skipsAgainUnopened(skippedBefore, fingerprint, minimumDurationMs)) {")
        assertTrue("checked before any tag read", again in 0 until picked.indexOf("extractId3Metadata("))
        assertTrue(picked.contains("skipped += SkippedLocalFile(id = id, fingerprint = fingerprint, durationMs = durationMs.coerceAtLeast(0L))"))
        assertTrue("a full rescan forgets them too", scanner.contains("val rememberedSkipped = if (cacheUsable) scanCache?.skippedFiles.orEmpty()"))
        assertTrue(scanner.contains("skippedFiles = snapshot.skippedFiles,"))
    }

    @Test
    fun `the automatic scan stamp moves with MediaStore and with the scan settings`() {
        val config = LocalSongScanConfig(minimumDurationSeconds = 30, includedFolders = setOf("Music/Rock"))
        val stamp = localAutoScanStamp(mapOf("external_primary" to "v1:10", "1a2b-3c4d" to "v2:5"), config)
        assertEquals(stamp, localAutoScanStamp(mapOf("1a2b-3c4d" to "v2:5", "external_primary" to "v1:10"), config))
        assertNotEquals(stamp, localAutoScanStamp(mapOf("external_primary" to "v1:11", "1a2b-3c4d" to "v2:5"), config))
        assertNotEquals(stamp, localAutoScanStamp(mapOf("external_primary" to "v1:10"), config))
        assertNotEquals(stamp, localAutoScanStamp(mapOf("external_primary" to "v1:10", "1a2b-3c4d" to "v2:5"), config.copy(minimumDurationSeconds = 0)))
        assertNotEquals(stamp, localAutoScanStamp(mapOf("external_primary" to "v1:10", "1a2b-3c4d" to "v2:5"), config.copy(includedFolders = emptySet())))
        assertNull("unknown MediaStore state: the scan runs", localAutoScanStamp(null, config))
        assertNull(localAutoScanStamp(emptyMap(), config))
        assertTrue(autoScanFindsNothingNew(stamp, stamp))
        assertFalse(autoScanFindsNothingNew(null, stamp))
        assertFalse(autoScanFindsNothingNew(stamp, null))
    }

    @Serializable
    private data class ShapeV1(val id: String, val names: List<Inner>)

    @Serializable
    private data class ShapeV2(val id: String, val names: List<Inner>, val discNumber: Int? = null)

    @Serializable
    private data class Inner(val name: String)

    @Serializable
    private data class Dated(@Serializable(with = IsoLocalDateTimeSerializer::class) val at: LocalDateTime?)

    @Test
    fun `a field added to the remembered track changes its shape, nested fields included`() {
        val v1 = ShapeV1.serializer().descriptor.shape()
        val v2 = ShapeV2.serializer().descriptor.shape()
        assertTrue(v1, v1.contains("names:") && v1.contains("name:kotlin.String"))
        assertNotEquals(v1.substringAfter('('), v2.substringAfter('('))
    }

    @Test
    fun `the modification date survives the round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val value = Dated(LocalDateTime.of(2026, 11, 26, 10, 15, 30))
        assertEquals(value, json.decodeFromString(Dated.serializer(), json.encodeToString(Dated.serializer(), value)))
        assertEquals(Dated(null), json.decodeFromString(Dated.serializer(), json.encodeToString(Dated.serializer(), Dated(null))))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val scanner by lazy { text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt") }

    @Test
    fun `an unchanged file is reused after the folder and format filters and never reopened`() {
        val audio = scanner.substringAfter("MediaStore returned no audio cursor").substringBefore("val videoProjection")
        val reuse = audio.indexOf("if (reuseIfUnchanged(contentUri.toString(), cursor.fingerprint(sizeIndex, dateModifiedIndex, generationIndex))) continue")
        assertTrue("unsupported files are still counted first", reuse > audio.indexOf("skippedUnsupported++"))
        assertTrue("the tag reader is never reached for a reused file", reuse in 0 until audio.indexOf("extractId3Metadata(contentUri, dataPath)"))
        val video = scanner.substringAfter("MediaStore returned no video cursor").substringBefore("val albums = tracks")
        assertTrue(video.contains("if (reuseIfUnchanged(contentUri.toString(), cursor.fingerprint(sizeIndex, dateModifiedIndex, generationIndex))) continue"))
        assertTrue("generation only where it exists", scanner.contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.GENERATION_MODIFIED)"))
    }

    @Test
    fun `the memory is written only after a successful transaction, on this phone only`() {
        val scan = scanner.substringAfter("suspend fun scanDevice(").substringBefore("suspend fun scanAutomatically(")
        val write = scan.indexOf("writeScanCache(")
        assertTrue(write > scan.indexOf("database.withTransaction {"))
        assertTrue("a full rescan reuses nothing", scan.contains("localScanCacheUsable(scanCache?.header, cacheHeader, fullRescan,"))
        assertTrue(scan.contains("val remembered = if (cacheUsable) scanCache?.tracks.orEmpty().associateBy { it.record.id } else emptyMap()"))
        assertTrue(scanner.contains("File(context.noBackupFilesDir, \"local_scan_tracks.json\")"))
    }

    @Test
    fun `a reused file whose row is current is not rewritten, and every check covers what the update writes`() {
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        assertTrue(transaction.contains("if (track.id in untouchedIds) return@forEach"))
        assertTrue("nothing changed and nothing went: no write at all", transaction.contains("if (removable.isEmpty() && untouchedIds.size == snapshot.tracks.size) {"))
        val check = scanner.substringAfter("private fun Song?.isUnchangedLocalRow(").substringBefore("\n    }\n")
        for (field in listOf("song.title", "song.duration", "song.albumId", "song.albumName", "song.isVideo", "song.dateModified", "song.year",
            "song.thumbnailUrl", "stored.contentLength", "stored.mimeType", "stored.loudnessDb", "artists.map { it.id }")) {
            assertTrue(field, check.contains(field))
        }
    }

    @Test
    fun `a scan nobody asked for never deletes a song it does not see`() {
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        assertTrue(transaction.contains("automatic -> missingOnMountedVolumes.filterNot(moved.moves::containsKey)"))
        val auto = scanner.substringAfter("suspend fun scanAutomatically(").substringBefore("suspend fun usesLocalLibrary(")
        assertTrue(auto.contains("return scanDevice(scanConfig, automatic = true)"))
        assertFalse("never the confirmed removal", auto.contains("removeMissing"))
        assertTrue("one scan at a time in the process", scanner.contains("= scanMutex.withLock { withContext(Dispatchers.IO) {"))
    }

    @Test
    fun `automatic scans are event-driven, debounced and only for local-music users`() {
        val auto = text("app/src/main/kotlin/com/music/echo/localmedia/LocalLibraryAutoScan.kt")
        assertTrue(auto.contains("registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)"))
        assertTrue(auto.contains("registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)"))
        assertTrue(auto.contains("requests.debounce(DebounceMs).collect { scanOnce() }"))
        assertTrue(auto.contains("if (!runCatching { scanner.usesLocalLibrary() }.getOrDefault(false)) return@launch"))
        assertFalse("no timer, no poll", Regex("""while\s*\(true\)|delay\(|PeriodicWorkRequest|AlarmManager""").containsMatchIn(auto))
        val log = auto.substringAfter("Timber.i(").substringBefore(")\n")
        assertTrue("counts only", log.contains("songs=%d videos=%d removed=%d kept=%d"))
        val app = text("app/src/main/kotlin/com/music/echo/App.kt")
        val defaultProcess = app.substringAfter("if (!isDefaultProcess()) return")
        assertTrue("started in the default process only", defaultProcess.contains("localLibraryAutoScan.get().ensureStarted()"))
        val viewModel = text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt")
        assertTrue("and after the user's first successful scan", viewModel.substringAfter(".onSuccess { summary ->").substringBefore(".onFailure").contains("localLibraryAutoScan.ensureStarted()"))
    }

    @Test
    fun `the full rescan is one tap away in the scan sheet`() {
        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
        assertTrue(screen.contains("onFullRescan = { viewModel.scanDevice(scanConfig, fullRescan = true) },"))
        assertTrue(screen.contains("onClick = { ActionLog.tap(ActionId.LOCAL_SCAN_FULL); onFullRescan() },"))
        assertTrue(text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt").contains("localSongScanner.scanDevice(scanConfig, removeMissing, fullRescan)"))
        for (values in listOf("values", "values-es")) {
            val strings = text("app/src/main/res/$values/echo_strings.xml")
            assertTrue(values, strings.contains("name=\"local_songs_full_rescan\""))
            assertTrue(values, strings.contains("name=\"local_songs_full_rescan_desc\""))
        }
    }
}
