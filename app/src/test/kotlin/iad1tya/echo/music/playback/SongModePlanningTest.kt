package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «si esos videos [de la lista Videos de un artista] no se pueden poner en modo canción, que no
 * salga modo canción, porque esto genera un error». Su log (10-19 23:41–23:44) muestra el mecanismo: el audio de
 * canción de esos ids lo rechaza `music.youtube.com` (UNPLAYABLE / 400) aunque PipePipe SÍ saca el vídeo, y el modo
 * vídeo vídeo-only vuelve a pedir ese mismo audio roto. Estas pruebas fijan las decisiones puras y que los puntos de
 * enganche existen (la mitad de este arreglo es cableado: una decisión correcta que nadie llama es un placebo).
 */
class SongModePlanningTest {

    @Test
    fun `Cancion no se ofrece en modo video para un video cuyo audio de cancion esta rechazado`() {
        assertFalse(SongModePlanning.offerSongOption(videoModeOn = true, isVideoSong = true, songBlocked = true))
    }

    @Test
    fun `Cancion se sigue ofreciendo en el resto de casos`() {
        assertTrue(SongModePlanning.offerSongOption(videoModeOn = true, isVideoSong = true, songBlocked = false))
        // Fuera del modo vídeo el botón que existe es «Vídeo»: nunca se esconde por esto.
        assertTrue(SongModePlanning.offerSongOption(videoModeOn = false, isVideoSong = true, songBlocked = true))
        // Un podcast o una canción normal no dependen de este registro.
        assertTrue(SongModePlanning.offerSongOption(videoModeOn = true, isVideoSong = false, songBlocked = true))
    }

    @Test
    fun `un video cuya cancion falla por contenido pasa a modo video, una sola vez`() {
        assertTrue(SongModePlanning.shouldFallBackToVideo(isVideoSong = true, contentLevel = true, videoModeOn = false, alreadyTried = false))
        // Ya se probó: el fallo sigue su camino de siempre (aviso + salto) — sin bucle canción→vídeo→canción.
        assertFalse(SongModePlanning.shouldFallBackToVideo(isVideoSong = true, contentLevel = true, videoModeOn = false, alreadyTried = true))
    }

    @Test
    fun `no hay respaldo a video para lo que no es un video ni para fallos de sesion o red`() {
        assertFalse(SongModePlanning.shouldFallBackToVideo(isVideoSong = false, contentLevel = true, videoModeOn = false, alreadyTried = false))
        assertFalse(SongModePlanning.shouldFallBackToVideo(isVideoSong = true, contentLevel = false, videoModeOn = false, alreadyTried = false))
        assertFalse(SongModePlanning.shouldFallBackToVideo(isVideoSong = true, contentLevel = true, videoModeOn = true, alreadyTried = false))
    }

    @Test
    fun `con el audio de cancion rechazado solo sirve el peldano progresivo`() {
        VideoFormatFallback.Source.entries.forEach { source ->
            val allowed = SongModePlanning.ladderSourceAllowed(source, songBlocked = true)
            assertEquals(source == VideoFormatFallback.Source.PIPEPIPE_MUXED, allowed)
            // Sin bloqueo la escalera es la de siempre, entera.
            assertTrue(SongModePlanning.ladderSourceAllowed(source, songBlocked = false))
        }
    }

    @Test
    fun `una URL de video-only en cache no vale para un id con la cancion rechazada`() {
        assertFalse(SongModePlanning.cachedVideoUsable(songBlocked = true, cachedIsMuxed = false))
        assertTrue(SongModePlanning.cachedVideoUsable(songBlocked = true, cachedIsMuxed = true))
        assertTrue(SongModePlanning.cachedVideoUsable(songBlocked = false, cachedIsMuxed = false))
    }

    @Test
    fun `la cadena de saltos se agota pasado el tope, no antes`() {
        val max = MusicService.MAX_CONSECUTIVE_ERR
        assertFalse(SongModePlanning.skipChainExhausted(max, max))
        assertTrue(SongModePlanning.skipChainExhausted(max + 1, max))
    }

    @Test
    fun `el registro recuerda, desmarca y esta acotado`() {
        val r = SongModeBlockedRegistry(maxEntries = 3)
        assertTrue(r.add("a"))
        assertFalse("repetir no cambia nada", r.add("a"))
        assertFalse(r.add(""))
        assertTrue(r.contains("a"))
        assertTrue(r.remove("a"))
        assertFalse(r.contains("a"))
        listOf("b", "c", "d", "e").forEach { r.add(it) }
        assertEquals("lo más antiguo sale primero", setOf("c", "d", "e"), r.snapshot())
    }

    // ── Cableado: las decisiones de arriba solo valen si alguien las llama ──────────────────────────

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root")
    }

    private fun src(path: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `el resolutor aprende el bloqueo y lo borra cuando el audio suena`() {
        val s = src("playback/StreamResolution.kt")
        assertTrue(s.contains("if (throwable.contentLevel) SongModeAvailability.markBlocked(mediaId)"))
        assertTrue(s.contains("SongModeAvailability.clear(mediaId)"))
    }

    @Test
    fun `el fallo de cancion de un video entra en modo video antes de avisar y saltar`() {
        val s = src("playback/MusicService.kt")
        val fallback = s.indexOf("videoCoordinator.fallBackToVideoForUnresolvableSong(mediaId)")
        val skip = s.indexOf("handleUnresolvableSong(mediaId, PlaybackErrorClassifier.noStreamReason(error))")
        assertTrue("falta el respaldo a vídeo", fallback > 0)
        assertTrue("el respaldo debe ir ANTES del aviso + salto", fallback < skip)
        assertTrue(s.contains("SongModePlanning.skipChainExhausted(consecutivePlaybackErr, MAX_CONSECUTIVE_ERR)"))
    }

    @Test
    fun `la escalera y la cache de video respetan el bloqueo`() {
        val s = src("playback/VideoModeCoordinator.kt")
        assertTrue(s.contains("SongModePlanning.ladderSourceAllowed(source, songBlocked)"))
        assertTrue(s.contains("SongModePlanning.cachedVideoUsable("))
        assertTrue(s.contains("if (SongModeAvailability.isBlocked(id)) return"))
    }

    @Test
    fun `los cuatro botones de salir del modo video consultan el bloqueo`() {
        assertTrue(src("ui/newui/AuraPlayer.kt").let {
            it.contains("SongModePlanning.offerSongOption(videoMode, meta.isVideoSong, meta.id in songBlockedIds)") &&
                it.contains("exitId !in songBlockedIds")
        })
        assertTrue(src("ui/newui/AuraPlayerMenu.kt").contains("SongModePlanning.offerSongOption(videoMode, mediaMetadata.isVideoSong"))
        val classic = src("ui/player/Player.kt")
        assertTrue(classic.contains("SongModePlanning.offerSongOption(videoMode, it.isVideoSong"))
        assertTrue(classic.contains("lsSongBlockedIds"))
    }

    @Test
    fun `el respaldo automatico no abre el reproductor a pantalla completa`() {
        assertTrue(src("MainActivity.kt").contains("consumeSilentVideoEntry()"))
    }
}
