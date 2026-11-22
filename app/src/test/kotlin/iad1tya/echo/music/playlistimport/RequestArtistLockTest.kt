package iad1tya.echo.music.playlistimport

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 651 (owner 2026-11-22): «Pedir música» with an exact artist («Luis Miguel») must give ONLY that artist, in the result
 * AND in the queue that continues it — Cristian Castro / Ricardo Montaner used to appear. The rule itself is pure
 * ([RequestArtistTargets.admits]); the wiring through the generator, the view model and the player is fixed by source.
 */
class RequestArtistLockTest {
    private val lock = RequestArtistTargets.Lock("Luis Miguel", "UC_REAL")

    @Test
    fun `only the named artist passes the lock`() {
        assertTrue(RequestArtistTargets.admits(lock, listOf("Luis Miguel" to "UC_REAL")))
        assertFalse(RequestArtistTargets.admits(lock, listOf("Cristian Castro" to "UC_CC")))
        assertFalse(RequestArtistTargets.admits(lock, listOf("Ricardo Montaner" to null)))
    }

    @Test
    fun `a namesake channel and a guest feature do not pass`() {
        assertFalse(RequestArtistTargets.admits(lock, listOf("Luis Miguel" to "UC_OTHER")))
        assertFalse(
            RequestArtistTargets.admits(lock, listOf("A" to "UC_A", "B" to "UC_B", "C" to "UC_C", "Luis Miguel" to "UC_REAL")),
        )
    }

    @Test
    fun `a duet with the artist passes, a song with no credits does not`() {
        assertTrue(RequestArtistTargets.admits(lock, listOf("Luis Miguel" to "UC_REAL", "Michael Bublé" to "UC_MB")))
        assertTrue(RequestArtistTargets.admits(RequestArtistTargets.Lock("Luis Miguel", null), listOf("Luis Miguel" to "UC_ANY")))
        assertFalse(RequestArtistTargets.admits(lock, emptyList()))
    }

    @Test
    fun `no lock keeps every song, so natural-language requests may still improvise`() {
        assertTrue(RequestArtistTargets.admits(null, listOf("Cristian Castro" to "UC_CC")))
        assertNull(RequestArtistTargets.get(null))
        assertNull(RequestArtistTargets.get("MR:never-stored"))
    }

    @Test
    fun `the lock is kept by queue context id, the last few only`() {
        RequestArtistTargets.put("MR:test-lock", lock)
        assertEquals(lock, RequestArtistTargets.get("MR:test-lock"))
        repeat(20) { RequestArtistTargets.put("MR:test-other-$it", lock) }
        assertNull(RequestArtistTargets.get("MR:test-lock"))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the generator hands the artist on and holds the AI answer to it`() {
        val gen = main("playlistimport/AiPlaylistGenerator.kt")
        assertTrue(gen.contains("val soloArtist = AiPlaylistConstraints.extractSoloArtist(intent.cleanPrompt) ?: lockedArtist"))
        assertTrue(gen.contains("lockedArtist = effectiveSolo.takeIf { artistLocked || soloArtist != null }"))
        val racing = gen.substringAfter("private suspend fun produceRacing(").substringBefore("private suspend fun produceInternal(")
        assertTrue("la IA temprana pasa por el candado", racing.contains("val aiEarly = lockedAi("))
        assertTrue("la IA tardía también", racing.contains("val aiLate = lockedAi("))
        assertTrue("y se le pide ese artista si la búsqueda falló", racing.contains("aiFlow(database, prompt, target, lock?.name,"))
        assertEquals("los dos retornos de la búsqueda llevan el candado", 2, racing.split("lockedArtist = lock?.name").size - 1)
    }

    @Test
    fun `the view model stores the lock for the queue, the top-up and the second option`() {
        val vm = main("viewmodels/MusicRequestViewModel.kt")
        assertTrue(vm.contains("RequestArtistTargets.Lock(it, produced.lockedArtistId)"))
        val fill = vm.substringAfter("private suspend fun fillInBackground(").substringBefore("fun reset()")
        assertTrue("el relleno no puede perder el artista", fill.contains("lockedArtist = iad1tya.echo.music.playlistimport.RequestArtistTargets.get(contextId)?.name"))
        val alt = vm.substringAfter("fun contextForAlternate()").substringBefore("fun saveAlternateAsPlaylist()")
        assertTrue(alt.contains("RequestArtistTargets.put(altId, it)"))
        assertTrue(vm.substringAfter("fun loadAlternate()").substringBefore("fun contextForAlternate()").contains("RequestArtistTargets.get(args.contextId)?.name"))
    }

    @Test
    fun `every song the smart queue appends passes the lock unless the user steered`() {
        val service = main("playback/MusicService.kt")
        val ordering = service.substringAfter("internal suspend fun List<MediaItem>.orderedByTaste(").substringBefore("val p = tasteProfile()")
        assertTrue(ordering.contains("if (exactStyle.chipSteered || activeMoodParams != null) null"))
        assertTrue(ordering.contains("RequestArtistTargets.get(radioOriginContextId)"))
        assertTrue("una sola canción tampoco se salta el candado", ordering.contains("if (size < 2 && !strictNoRepeat && artistLock == null) return this"))
        assertTrue(ordering.indexOf("RequestArtistTargets.admits(") in 0 until ordering.indexOf("val m = mi.metadata ?: return@filter true"))
    }

    @Test
    fun `the continuation draws from the artist's own catalogue first, never from similar artists`() {
        val engine = main("playback/RadioContinuationEngine.kt")
        val chain = "tryMood() || tryArtistCatalogue() || (!artistLockExhausted && (tryContextRadio() || tryRadio() || tryRelated()))"
        assertEquals("una sola escalera, el catálogo antes que las radios", 1, engine.split(chain).size - 1)
        assertEquals("los seis intentos la usan", 6, engine.split("appended = trySources()").size - 1)
        assertFalse(engine.contains("tryMood() || tryContextRadio()"))
        val catalogue = main("playback/RequestArtistCatalogue.kt")
        assertTrue(catalogue.contains("RequestArtistTargets.admits(lock, song.artists.map { it.name to it.id })"))
        for (forbidden in listOf("ArtistItem", "YouTube.next(", "RDAMVM", "related", "Timber")) {
            assertFalse("el catálogo no usa $forbidden", catalogue.contains(forbidden))
        }
    }

    @Test
    fun `an exhausted catalogue ends the queue without the other rungs or the relaxed retries`() {
        // Row 656: under the lock every other source is reduced to that artist; running them all (and the waits and
        // relaxation levels) at every end of queue spent ~40 requests for nothing.
        val engine = main("playback/RadioContinuationEngine.kt")
        val body = engine.substringAfter("suspend fun tryArtistCatalogue(): Boolean").substringBefore("radioSourceFailed(\"artist\", it)")
        assertTrue(body.indexOf("artistLockExhausted = true") > body.indexOf("for (batch in unheard.chunked(ARTIST_CATALOGUE_BATCH)"))
        assertEquals("los cinco reintentos se saltan", 5, engine.split("if (!appended && !artistLockExhausted) {").size - 1)
        assertTrue("el aviso único de «cola terminada» sigue ahí", engine.contains("smartQueueEndNoticeGeneration = seedGeneration"))
    }

    @Test
    fun `a failed catalogue fetch is asked again, only an answer is cached`() {
        val catalogue = main("playback/RequestArtistCatalogue.kt")
        assertTrue(catalogue.contains("internal suspend fun requestArtistCatalogue(lock: RequestArtistTargets.Lock): List<MediaItem>? {"))
        assertTrue(catalogue.contains("YouTube.search(lock.name, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: return null"))
        val engine = main("playback/RadioContinuationEngine.kt")
        val body = engine.substringAfter("suspend fun tryArtistCatalogue(): Boolean").substringBefore("radioSourceFailed(\"artist\", it)")
        assertTrue(body.contains("?.also { artistCatalogue = it }"))
        assertTrue(body.contains("?: return@runCatching false"))
    }

    @Test
    fun `a chip or a mood stands the lock down in the source too, like in the ordering`() {
        val engine = main("playback/RadioContinuationEngine.kt")
        assertTrue(engine.contains("if (activeMoodParams != null || exactStyle.chipSteered) return@runCatching false"))
    }

    @Test
    fun `a queue adopted outside playQueue drops the request context`() {
        // Row 656: «Reproducir a continuación» on a cleared player (and Listen Together) adopt a new queue; it kept the
        // request's MR id, so its end brought that artist back and dropped everything else.
        val service = main("playback/MusicService.kt")
        val adopt = service.substringAfter("fun adoptDirectQueue(").substringBefore("fun adoptExternalQueue(")
        assertTrue(adopt.contains("radioOriginContextId = contextId"))
    }
}
