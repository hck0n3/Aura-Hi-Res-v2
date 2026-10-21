package iad1tya.echo.music.playlistimport

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-10-19): *«en Pedir música, si necesito una canción en específico en karaoke, que la pueda pedir de ahí para
 * que me ponga la pista para que yo la pueda cantar»*. La lectura de la petición y la elección de las pistas se prueban en
 * `MusicRequestKaraokeTest`; esto fija el CABLEADO de lo que no corre en la JVM (el generador habla con YouTube, el servicio
 * con media3): que la ruta del karaoke exista, sea cerrada y no se desvíe por la escalera genérica.
 */
class MusicRequestKaraokeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val generator get() = main("playlistimport/AiPlaylistGenerator.kt")

    private fun karaokeSearch(): String =
        generator.substringAfter("private suspend fun karaokeSearch(").substringBefore("🔴 QUIEN ESTÉ LISTO")

    @Test
    fun `produce hands a backing-track request to its own ladder before the generic one`() {
        val produce = generator.substringAfter("suspend fun produce(").substringBefore("private suspend fun produceRacing(")
        val route = produce.indexOf("MusicRequestKaraoke.parse(prompt)")
        assertTrue("la ruta del karaoke existe", route > 0)
        assertTrue("antes de la escalera genérica", route < produce.indexOf("produceRacing("))
        assertTrue("y antes del candado de artista único", route < produce.indexOf("AiPlaylistConstraints.extractSoloArtist"))
        // Y no pasa por el filtro anti-karaoke de salida: es justo lo que se pidió.
        val branch = produce.substring(route, produce.indexOf("val soloArtist"))
        assertFalse(branch.contains("MusicRequestInstrumental.filter"))
        assertTrue("sin resultado es un «no encontré nada» honesto, no el original", branch.contains("return karaoke"))
    }

    @Test
    fun `the ladder searches karaoke songs AND videos, keeps only what select keeps, and never fills with other songs`() {
        val body = karaokeSearch()
        assertTrue(body.contains("YouTube.SearchFilter.FILTER_SONG") && body.contains("YouTube.SearchFilter.FILTER_VIDEO"))
        assertTrue(body.contains("MusicRequestKaraoke.select(ask, pool, wide)"))
        assertTrue("la otra clase de pista y el «backing track»", body.contains("ask.altQuery") && body.contains("ask.backingQuery"))
        // Lo que arrastraba antes más karaoke ajeno o el original:
        for (forbidden in listOf("YouTube.next(", "dominantArtist(", "moodCategoryPlaylists(", "aiFlow(", "MusicRequestStyleGate", "trendingSongs(")) {
            assertFalse("la ruta del karaoke no usa $forbidden", body.contains(forbidden))
        }
        // Las listas de la gente solo para un artista o un estilo, y solo sus pistas pasan por select.
        assertTrue(body.contains("ask.styleOnly || picked.kind != MusicRequestKaraoke.Kind.SONG"))
    }

    @Test
    fun `one song is exempt from the recents rule, an artist or a style is not`() {
        val body = karaokeSearch()
        val song = body.substringAfter("if (kind == MusicRequestKaraoke.Kind.SONG) {").substringBefore("} else {")
        assertFalse("pedir la misma pista otra vez da la misma pista", song.contains("MusicRequestRecents"))
        val other = body.substringAfter("} else {").substringBefore("timber.log.Timber.i(\"MUSIC_REQUEST karaoke kind=")
        assertTrue(other.contains("MusicRequestRecents.isRecent") && other.contains("MusicRequestRecents.markServed"))
    }

    @Test
    fun `the card is named after what is sung and the list says whether it is closed`() {
        assertTrue(generator.contains("val karaoke: MusicRequestKaraoke.Kind? = null"))
        assertTrue(karaokeSearch().contains("MusicRequestKaraoke.label(ask, kind, picked.best?.title, MAX_NAME_LENGTH)"))
        assertTrue(karaokeSearch().contains("karaoke = kind"))
    }

    @Test
    fun `a closed one-song list is not topped up and its queue brings no karaoke of other songs`() {
        val vm = main("viewmodels/MusicRequestViewModel.kt")
        assertTrue(vm.contains("produced.karaoke == iad1tya.echo.music.playlistimport.MusicRequestKaraoke.Kind.SONG"))
        assertTrue(vm.contains("RequestKaraokeContexts.markClosed(contextId)"))
        assertTrue(vm.contains("val fillWillRun = firstSongs.size < requestedTotal && !closedKaraoke"))
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("RequestKaraokeContexts.isClosed(radioOriginContextId)"))
        assertTrue(
            "los derivados se sueltan de la cola que sigue",
            service.contains("if (karaokeClosed && iad1tya.echo.music.reco.VersionNoise.isDerivative(m.title, m.album?.title)) return@filter false"),
        )
        assertTrue("y ya no cuenta como 'ánimo de karaoke'", service.contains("val inDerivativeMood = !karaokeClosed && nowMeta != null &&"))
    }

    @Test
    fun `the log carries counts only - never a word of the request or a title`() {
        // AGENTS.md regla 4: el registro lo comparte el usuario.
        val lines = generator.lines().filter { it.contains("MUSIC_REQUEST karaoke") || it.contains("MUSIC_REQUEST source=karaoke") }
        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertFalse("sin la petición en el registro: $line", line.contains("ask.subject") || line.contains("prompt") || line.contains(".title"))
        }
        assertFalse(main("playlistimport/MusicRequestKaraoke.kt").contains("Timber"))
    }

    @Test
    fun `the old anti-karaoke filter knows the other spellings and stays for every other request`() {
        val instrumental = main("playlistimport/MusicRequestInstrumental.kt")
        assertTrue(instrumental.contains("\"caraoke\", \"caraokes\", \"karaoki\""))
        assertTrue("el filtro de salida sigue en la ruta genérica", generator.contains("MusicRequestInstrumental.filter(prompt, r.songs)"))
    }
}
