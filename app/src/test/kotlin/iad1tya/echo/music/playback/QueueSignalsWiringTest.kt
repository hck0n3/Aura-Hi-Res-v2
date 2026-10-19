package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The smart-queue signals ([iad1tya.echo.music.reco.SessionSkipMemory], [iad1tya.echo.music.reco.VersionNoise],
 * [ArtistSpacing]'s lead-in) are only worth anything if the service really feeds and reads them. This pins the
 * wiring in source — the same convention as the other guard tests — so a refactor cannot leave them dead.
 */
class QueueSignalsWiringTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the ordering reads the three signals and the batch seam is spaced`() {
        val service = main("playback/MusicService.kt")
        val ordering = service.substringAfter("internal suspend fun List<MediaItem>.orderedByTaste(").substringBefore("// Artist spacing and the exploration quota moved")
        assertTrue(ordering.contains("skipMemory.penalty("))
        assertTrue("el diseño por colección conserva a sus artistas", ordering.contains("if (m == null || usePattern) 0.0"))
        assertTrue(ordering.contains("VersionNoise.isDerivative(m.title, m.album?.title)"))
        assertTrue("en modo karaoke/instrumental no se castiga", ordering.contains("inDerivativeMood ||"))
        assertTrue(ordering.contains("+ sunk + skipDemotion + versionDemotion"))
        assertTrue(ordering.contains("RadioQueueShaping.spacedByArtist(rankedUnheard, queued?.tailArtists.orEmpty())"))
    }

    @Test
    fun `queue pages follow the end of the queue, replaced tails follow the current song`() {
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("val queuedNow = liveQueueSnapshot(afterCurrent = false)"))
        assertTrue(service.contains("val anchor = if (afterCurrent) player.currentMediaItemIndex else count - 1"))
    }

    @Test
    fun `skips are learned for every song and the crossfade swap keeps the hook pointed at the playing song`() {
        val gate = main("playback/ExactStyleGate.kt")
        val hook = gate.substringAfter("internal fun MusicService.exactStyleOnTransition(")
        assertTrue(hook.contains("st.quickSkips.record(previous.artists.firstOrNull()?.name)"))
        assertTrue(
            "la memoria de saltos aprende ANTES de la salida temprana de las canciones sin filtrar",
            hook.indexOf("st.quickSkips.record(") in 0 until hook.indexOf("previous.id !in st.gatedIds"),
        )
        assertTrue(gate.contains("quickSkips.clear()"))
        val service = main("playback/MusicService.kt")
        val advance = service.substringAfter("internal fun applyAutoAdvanceSideEffects()").take(2500)
        assertTrue(advance.contains("exactStyleOnTransition(it, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)"))
    }

    @Test
    fun `no title, artist or id reaches the log`() {
        val service = main("playback/MusicService.kt")
        val log = service.substringAfter("\"QUEUE_SIGNALS demoted:").substringBefore(")")
        assertTrue(log.contains("skippedArtist=%d reupload=%d of %d candidates"))
    }
}
