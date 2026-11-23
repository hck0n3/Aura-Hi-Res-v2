package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 724: ReplayGain tags of local files, read once per file and stored as `FormatEntity.loudnessDb`. */
class ReplayGainTagsTest {

    @Test
    fun `tag values in the usual spellings parse`() {
        assertEquals(-6.54, parseReplayGainDb("-6.54 dB")!!, 1e-9)
        assertEquals(1.2, parseReplayGainDb("+1.20 dB")!!, 1e-9)
        assertEquals(-6.54, parseReplayGainDb(" -6,54 dB")!!, 1e-9)
        assertEquals(3.0, parseReplayGainDb("3")!!, 1e-9)
        assertEquals(-0.5, parseReplayGainDb("-.5 dB")!!, 1e-9)
    }

    @Test
    fun `broken tag values are ignored`() {
        assertNull(parseReplayGainDb(null))
        assertNull(parseReplayGainDb(""))
        assertNull(parseReplayGainDb("dB"))
        assertNull(parseReplayGainDb("NaN dB"))
        assertNull(parseReplayGainDb("-999 dB"))
    }

    @Test
    fun `track gain wins over album gain, in any key spelling`() {
        val flac = mapOf("ALBUM" to "x", "REPLAYGAIN_ALBUM_GAIN" to "-4.00 dB", "REPLAYGAIN_TRACK_GAIN" to "-7.50 dB")
        assertEquals(-7.5, replayGainFromTags(listOf(flac))!!, 1e-9)
        val itunes = mapOf("----:com.apple.iTunes:replaygain_track_gain" to "-2.10 dB")
        assertEquals(-2.1, replayGainFromTags(listOf(emptyMap(), itunes))!!, 1e-9)
        val id3 = mapOf("replaygain_track_gain" to "+0.80 dB")
        assertEquals(0.8, replayGainFromTags(listOf(id3))!!, 1e-9)
    }

    @Test
    fun `album gain is the fallback and stream tags count too`() {
        val format = mapOf("title" to "x")
        val stream = mapOf("REPLAYGAIN_ALBUM_GAIN" to "-5.25 dB")
        assertEquals(-5.25, replayGainFromTags(listOf(format, stream))!!, 1e-9)
        assertEquals(-5.25, replayGainFromTags(listOf(mapOf("REPLAYGAIN_TRACK_GAIN" to "junk"), stream))!!, 1e-9)
    }

    @Test
    fun `no ReplayGain means no value, so the file keeps today's behaviour`() {
        assertNull(replayGainFromTags(emptyList()))
        assertNull(replayGainFromTags(listOf(mapOf("REPLAYGAIN_TRACK_PEAK" to "0.98", "R128_TRACK_GAIN" to "-512"))))
    }

    @Test
    fun `the gain maps onto the -14 LUFS loudnessDb the player levels by`() {
        assertEquals(-4.0, REPLAYGAIN_REFERENCE_LUFS - LOUDNESS_DB_REFERENCE_LUFS, 1e-9)
        // -8 LUFS master: gain -10 → 6 dB above -14 LUFS → the player applies -6 dB.
        assertEquals(6.0, replayGainToLoudnessDb(-10.0), 1e-9)
        // -20 LUFS quiet master: gain +2 → 6 dB below -14 LUFS → the player boosts +6 dB.
        assertEquals(-6.0, replayGainToLoudnessDb(2.0), 1e-9)
        // Exactly -14 LUFS: gain -4 → nothing to do.
        assertEquals(0.0, replayGainToLoudnessDb(-4.0), 1e-9)
    }

    @Test
    fun `the read cache round-trips and skips broken lines`() {
        val entries = mapOf(
            "content://media/external/audio/media/1" to ReplayGainCacheEntry(1000L, 1700000000L, 6.0),
            "content://media/external/audio/media/2" to ReplayGainCacheEntry(2000L, 1700000001L, null),
        )
        val text = serializeReplayGainCache(entries)
        assertEquals(entries, parseReplayGainCache(text.lines()))
        val broken = listOf("", "id\t1\t2", "id\tx\t2\t", "id\t1\t2\tabc", "ok\t1\t2\t-3.5")
        assertEquals(mapOf("ok" to ReplayGainCacheEntry(1L, 2L, -3.5)), parseReplayGainCache(broken))
    }

    @Test
    fun `a cached value is used only while the file is unchanged`() {
        val cache = mapOf("a" to ReplayGainCacheEntry(10L, 20L, 4.0))
        assertEquals(4.0, cachedReplayGainLoudness(cache, "a", 10L, 20L)!!, 1e-9)
        assertNull("size changed", cachedReplayGainLoudness(cache, "a", 11L, 20L))
        assertNull("rewritten", cachedReplayGainLoudness(cache, "a", 10L, 21L))
        assertNull("never read", cachedReplayGainLoudness(cache, "b", 10L, 20L))
    }

    @Test
    fun `only new or changed files are read, and never more than the bound`() {
        val cache = mapOf(
            "same" to ReplayGainCacheEntry(1L, 1L, null),
            "changed" to ReplayGainCacheEntry(1L, 1L, 2.0),
        )
        val files = listOf(Triple("same", 1L, 1L), Triple("changed", 1L, 2L), Triple("new1", 5L, 5L), Triple("new2", 6L, 6L))
        assertEquals(listOf("changed", "new1", "new2"), replayGainReadsNeeded(files, cache, maxReads = 10))
        assertEquals(listOf("changed", "new1"), replayGainReadsNeeded(files, cache, maxReads = 2))
        assertEquals(emptyList<String>(), replayGainReadsNeeded(files, cache, maxReads = 0))
    }

    private val scanner: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt").readText()
    }

    @Test
    fun `the scan reads tags before its transaction and stores them as loudnessDb`() {
        val scan = scanner.substringAfter("suspend fun scanDevice(")
        val beforeTransaction = scan.substringBefore("database.withTransaction {")
        assertTrue("tags are read outside the DB transaction", beforeTransaction.contains("val replayGain = readReplayGain(snapshot.tracks)"))
        assertTrue(
            scan.contains("loudnessDb = cachedReplayGainLoudness(replayGain, track.id, track.sizeBytes, track.modifiedEpochSeconds),"),
        )
        assertFalse("the scan no longer wipes every local loudness to null", scan.substringBefore("pruneLocalAlbums()").contains("loudnessDb = null,"))
        assertTrue("the read stamp is the raw MediaStore value", scanner.contains("modifiedEpochSeconds = cursor.getLong(dateModifiedIndex).coerceAtLeast(0L),"))
    }

    @Test
    fun `the reads are bounded, cancellable and remembered on this phone only`() {
        assertTrue(scanner.contains("const val MaxReplayGainReadsPerScan = 200"))
        assertTrue(scanner.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(scanner.contains("if (SystemClock.elapsedRealtime() > deadline) break"))
        assertTrue(scanner.contains("File(context.noBackupFilesDir, \"local_replaygain.tsv\")"))
        assertTrue("a broken ffmpeg load never fails the scan", scanner.contains("} catch (_: LinkageError) {"))
    }
}
