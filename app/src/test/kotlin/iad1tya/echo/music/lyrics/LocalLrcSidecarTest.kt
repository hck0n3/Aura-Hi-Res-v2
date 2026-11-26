package iad1tya.echo.music.lyrics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 884: `.lrc` files next to a song on this phone. */
class LocalLrcSidecarTest {
    @Test
    fun `the sidecar has the audio file's name with lrc or LRC`() {
        assertEquals(listOf("Song.lrc", "Song.LRC"), LocalLrcSidecar.sidecarNames("Song.flac"))
        assertEquals(listOf("01 - A.b.lrc", "01 - A.b.LRC"), LocalLrcSidecar.sidecarNames("01 - A.b.mp3"))
        assertEquals(listOf("NoExt.lrc", "NoExt.LRC"), LocalLrcSidecar.sidecarNames("NoExt"))
        assertEquals(listOf(".hidden.lrc", ".hidden.LRC"), LocalLrcSidecar.sidecarNames(".hidden"))
        assertTrue(LocalLrcSidecar.sidecarNames("").isEmpty())
        assertTrue(LocalLrcSidecar.sidecarNames(null).isEmpty())
        assertTrue(LocalLrcSidecar.sidecarNames("a/b.mp3").isEmpty())
    }

    @Test
    fun `utf8 with BOM and legacy windows-1252 files both decode`() {
        assertEquals("[00:01.00]Canción", LocalLrcSidecar.decode("\uFEFF[00:01.00]Canción".toByteArray(Charsets.UTF_8)))
        assertEquals("[00:01.00]Canción", LocalLrcSidecar.decode("[00:01.00]Canción".toByteArray(charset("windows-1252"))))
    }

    @Test
    fun `timestamps become the form the parser reads and ID tags go`() {
        val raw = "\uFEFF[ar:Someone]\r\n[ti:Song]\r\n[offset:+100]\r\n[0:05]Hola\r\n[00:07.5]Mundo\r\n[01:02:30]Fin\r\n"
        assertEquals("[00:05.00]Hola\n[00:07.50]Mundo\n[01:02.30]Fin", LocalLrcSidecar.normalize(raw))
        assertEquals("[00:01.234]x", LocalLrcSidecar.normalize("[00:01.234]x"))
        assertEquals("[00:01.00][00:30.00]chorus", LocalLrcSidecar.normalize("[00:01][00:30]chorus"))
        assertEquals("out of range seconds stay as they are", "[00:01.00]a\n[00:75]x", LocalLrcSidecar.normalize("[00:01]a\n[00:75]x"))
    }

    @Test
    fun `synced output matches the parser's line regex`() {
        // LyricsUtils.LINE_REGEX / TIME_REGEX, copied: the player only shows lines in this form.
        val line = Regex("((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)")
        val text = LocalLrcSidecar.normalize("[length:03:00]\n[1:05.3]Uno\n[01:06]Dos")!!
        assertTrue(text.startsWith("["))
        assertTrue(text.lines().all { line.matches(it) })
        val utils = File(repoRoot(), "app/src/main/kotlin/com/music/echo/lyrics/LyricsUtils.kt").readText()
        assertTrue(utils.contains("val LINE_REGEX = \"((\\\\[\\\\d\\\\d:\\\\d\\\\d\\\\.\\\\d{2,3}\\\\] ?)+)(.+)\".toRegex()"))
    }

    @Test
    fun `plain lyrics stay plain and files without lyrics are rejected`() {
        assertEquals("Line one\nLine two", LocalLrcSidecar.normalize("Line one\r\nLine two\r\n"))
        assertNull(LocalLrcSidecar.normalize("[ar:Only tags]\n[ti:Nothing else]\n"))
        assertNull(LocalLrcSidecar.normalize("   \n\n"))
        assertNull("the lyrics screen would take it for synced and show nothing", LocalLrcSidecar.normalize("[Verse 1]\nWords"))
    }

    @Test
    fun `a granted folder only reaches files inside it on the same volume`() {
        assertEquals("primary:Music/Album/Song.lrc", LocalLrcSidecar.documentIdInTree("primary:Music", "external_primary", "Music/Album/", "Song.lrc"))
        assertEquals("primary:Music/Song.lrc", LocalLrcSidecar.documentIdInTree("primary:", "external_primary", "Music/", "Song.lrc"))
        assertEquals("1A2B-3C4D:Music/Song.lrc", LocalLrcSidecar.documentIdInTree("1A2B-3C4D:Music", "1a2b-3c4d", "Music/", "Song.lrc"))
        assertNull(LocalLrcSidecar.documentIdInTree("primary:Music", "external_primary", "Podcasts/", "Song.lrc"))
        assertNull("prefix of another folder", LocalLrcSidecar.documentIdInTree("primary:Mus", "external_primary", "Music/", "Song.lrc"))
        assertNull(LocalLrcSidecar.documentIdInTree("primary:Music", "1a2b-3c4d", "Music/", "Song.lrc"))
        assertNull(LocalLrcSidecar.documentIdInTree("nocolon", "external_primary", "Music/", "Song.lrc"))
    }

    @Test
    fun `only a no-lyrics row nobody typed is replaced, and only for a phone song`() {
        val nf = "LYRICS_NOT_FOUND"
        assertTrue(LocalLrcSidecar.mayReplaceStored(true, nf, false, nf))
        assertTrue(LocalLrcSidecar.mayReplaceStored(true, " ", false, nf))
        assertFalse(LocalLrcSidecar.mayReplaceStored(true, "[00:01.00]real", false, nf))
        assertFalse(LocalLrcSidecar.mayReplaceStored(true, nf, true, nf))
        assertFalse(LocalLrcSidecar.mayReplaceStored(false, nf, false, nf))
    }

    @Test
    fun `the pipeline asks for the sidecar first and never logs where it is`() {
        val root = repoRoot()
        val helper = File(root, "app/src/main/kotlin/com/music/echo/lyrics/LyricsHelper.kt").readText()
        val getLyrics = helper.substringAfter("suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {")
        assertTrue(
            "before the network check and every online provider",
            getLyrics.indexOf("localSidecarLyrics(mediaMetadata.id)?.let { sidecar ->") in 0 until getLyrics.indexOf("networkConnectivity.isCurrentlyConnected()"),
        )
        val all = helper.substringAfter("suspend fun getAllLyrics(")
        assertTrue(all.indexOf("val sidecar = localSidecarLyrics(mediaId)") in 0 until all.indexOf("networkConnectivity.isCurrentlyConnected()"))
        assertTrue(helper.contains("if (!mediaId.isLocalMediaId()) return null"))
        val view = File(root, "app/src/main/kotlin/com/music/echo/ui/player/InlineLyricsView.kt").readText()
        assertTrue(view.contains("LocalLrcSidecar.mayReplaceStored("))
        assertTrue(view.contains("val sidecar = lyricsHelper.localSidecarLyrics(meta.id) ?: return@withContext"))
        val reader = File(root, "app/src/main/kotlin/com/music/echo/lyrics/LocalLrcReader.kt").readText()
        assertFalse("rule 4: no path or file name in the shared log", Regex("Timber|Log\\.|AppLogger").containsMatchIn(reader))
    }

    private fun repoRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
}
