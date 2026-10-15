package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.utils.COVER_LIST_BUCKET
import iad1tya.echo.music.ui.utils.COVER_LIST_BUCKET_LEAN
import iad1tya.echo.music.ui.utils.COVER_PLAYER_BUCKET
import iad1tya.echo.music.ui.utils.COVER_PLAYER_BUCKET_LEAN
import iad1tya.echo.music.ui.utils.canonicalCoverSize
import iad1tya.echo.music.utils.LeanMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 395 (owner 2026-10-15): «verifica cómo se comportará en dispositivos de gama muy baja… optimiza la app
 * al máximo… sin afectar nada de lo que ya tenemos para gama alta».
 *
 * Every lean saving hangs on ONE switch ([LeanMode], the Performance mode that already switches itself on in low-end
 * hardware). These pin that the switch really gates each saving AND that with it off nothing changed.
 */
class LeanModeGuardTest {
    @Test
    fun `lean mode is off before any preference has been read, which is the normal path`() {
        assertFalse(LeanMode.isOn())
    }

    @Test
    fun `cover sizes are byte-identical with lean off and smaller with lean on`() {
        // Lean OFF (the default): exactly the pre-existing buckets.
        assertEquals(COVER_LIST_BUCKET, canonicalCoverSize(256))
        assertEquals(COVER_LIST_BUCKET, canonicalCoverSize(640))
        assertEquals(COVER_PLAYER_BUCKET, canonicalCoverSize(1200))
        assertEquals(COVER_LIST_BUCKET, canonicalCoverSize(256, lean = false))
        // Lean ON: a 48 dp row stops downloading the 544 px file, the hero stops downloading the 1200 px one.
        assertEquals(COVER_LIST_BUCKET_LEAN, canonicalCoverSize(256, lean = true))
        assertEquals(COVER_LIST_BUCKET_LEAN, canonicalCoverSize(512, lean = true))
        assertEquals(COVER_PLAYER_BUCKET_LEAN, canonicalCoverSize(640, lean = true))
        assertEquals(COVER_PLAYER_BUCKET_LEAN, canonicalCoverSize(1200, lean = true))
        assertTrue(COVER_LIST_BUCKET_LEAN < COVER_LIST_BUCKET && COVER_PLAYER_BUCKET_LEAN < COVER_PLAYER_BUCKET)
        assertEquals(0, canonicalCoverSize(0, lean = true))
    }

    private val root: File by lazy {
        var d = File("").absoluteFile
        while (!File(d, "settings.gradle.kts").isFile) d = d.parentFile ?: error("repo root not found")
        d
    }

    private fun src(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `each lean saving is gated by the one switch`() {
        // Navigation: no transition.
        val nav = src("ui/component/AuraNavMotion.kt")
        assertTrue(nav.contains("LeanMode.isOn()) EnterTransition.None"))
        assertTrue(nav.contains("LeanMode.isOn()) ExitTransition.None"))
        // Hero: no blur sampling, smaller decode, in the three hero screens.
        for (f in listOf("AuraAlbumScreen.kt", "AuraArtistScreen.kt", "AuraOnlinePlaylistScreen.kt")) {
            val s = src("ui/newui/$f")
            assertTrue("$f", s.contains("val lean = rememberLeanMode()"))
            assertTrue("$f", s.contains("if (lean) Modifier else Modifier.hazeSource(heroHaze)"))
            assertTrue("$f", s.contains("decodeTo = if (lean) 640 else 1200"))
            assertTrue("$f", s.contains("if (lean) {"))
        }
        // Player / queue ground: the blurred cover is dropped.
        assertTrue(src("ui/newui/AuraPlayer.kt").contains("!isLocalMedia && !highPerfMode &&"))
        // Shared transition off.
        assertTrue(src("ui/newui/AuraSharedTransition.kt").contains("if (iad1tya.echo.music.utils.rememberLeanMode()) return this"))
        // Lyrics: coarse clock only with the mode on, the frame ticker untouched otherwise.
        val lyrics = src("ui/component/Lyrics.kt")
        assertTrue(lyrics.contains("if (perfOn) {\n                tick()\n                delay(120)"))
        assertTrue(lyrics.contains("withFrameMillis { tick() }"))
        // Playback: lean buffer, SponsorBlock off.
        val service = src("playback/MusicService.kt")
        assertTrue(service.contains("val maxBufferMs = if (useSmallBuffer) 60_000 else 200_000"))
        assertTrue(service.contains("!(it[iad1tya.echo.music.constants.HighPerformanceModeKey] ?: false)"))
        // Home: the hidden shelves are not fetched.
        val home = src("viewmodels/HomeViewModel.kt")
        assertTrue(home.contains("if (!lean) launch(Dispatchers.IO) { getCommunityPlaylists() }"))
        assertTrue(home.contains("if (!iad1tya.echo.music.utils.LeanMode.isOn()) {"))
    }
}
