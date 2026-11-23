package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.audio.AudioEffectSessionPlan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 723 — external equalizers (system EQ, Wavelet, JamesDSP) keep their audio session across crossfades.
 * The decision is tested as a pure function; the wiring (every place the live player changes) by source text,
 * because the players and the broadcasts only exist on a device.
 */
class AudioEffectSessionHandoverTest {
    private val none = 0 // C.AUDIO_SESSION_ID_UNSET

    @Test
    fun `the live session is announced once, never twice`() {
        val first = AudioEffectSessionPlan.next(announced = emptySet(), liveSessionId = 11, fadingSessionId = none)
        assertEquals(emptyList<Int>(), first.close)
        assertEquals(11, first.open)
        val again = AudioEffectSessionPlan.next(announced = setOf(11), liveSessionId = 11, fadingSessionId = none)
        assertEquals(emptyList<Int>(), again.close)
        assertNull("no duplicate OPEN for a session already announced", again.open)
    }

    @Test
    fun `a crossfade announces the incoming session and keeps the fading one`() {
        val swap = AudioEffectSessionPlan.next(announced = setOf(11), liveSessionId = 22, fadingSessionId = 11)
        assertEquals("the outgoing track is still audible during the fade", emptyList<Int>(), swap.close)
        assertEquals(22, swap.open)
    }

    @Test
    fun `the end of the fade closes the released player's session`() {
        val cleanup = AudioEffectSessionPlan.next(announced = setOf(11, 22), liveSessionId = 22, fadingSessionId = none)
        assertEquals(listOf(11), cleanup.close)
        assertNull(cleanup.open)
    }

    @Test
    fun `a pause during the fade closes only the live session`() {
        val pause = AudioEffectSessionPlan.next(announced = setOf(11, 22), liveSessionId = none, fadingSessionId = 11)
        assertEquals(listOf(22), pause.close)
        assertNull(pause.open)
    }

    @Test
    fun `an instant swap closes the old session and opens the new one`() {
        val swap = AudioEffectSessionPlan.next(announced = setOf(11), liveSessionId = 33, fadingSessionId = none)
        assertEquals(listOf(11), swap.close)
        assertEquals(33, swap.open)
    }

    @Test
    fun `pause closes and resume reopens the same session`() {
        assertEquals(listOf(11), AudioEffectSessionPlan.next(setOf(11), none, none).close)
        assertEquals(11, AudioEffectSessionPlan.next(emptySet(), 11, none).open)
    }

    @Test
    fun `a session id media3 has not generated yet is never announced`() {
        val pending = AudioEffectSessionPlan.next(announced = emptySet(), liveSessionId = none, fadingSessionId = none)
        assertEquals(emptyList<Int>(), pending.close)
        assertNull(pending.open)
    }

    // ── Wiring ──────────────────────────────────────────────────────────────────────────────────────────────────

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val service by lazy { main("playback/MusicService.kt") }
    private val crossfade by lazy { main("playback/CrossfadeEngine.kt") }

    /** From [signature] to the first line holding only [closing] (the end of that function). */
    private fun body(src: String, signature: String, closing: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n$closing\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    private fun assertAfter(text: String, first: String, then: String) {
        val a = text.indexOf(first)
        assertTrue("not found: $first", a >= 0)
        assertTrue("$then must come after $first", text.indexOf(then, a) > a)
    }

    @Test
    fun `every place the live player changes re-announces the session`() {
        val swap = body(crossfade, "internal fun MusicService.performCrossfadeSwap() {", "}")
        assertAfter(swap, "fadingPlayer = currentPlayer", "syncAudioEffectSessions()")
        assertAfter(swap, "player = nextPlayer", "syncAudioEffectSessions()")

        val cleanup = body(crossfade, "internal fun MusicService.cleanupCrossfade() {", "}")
        assertAfter(cleanup, "fadingPlayer = null", "syncAudioEffectSessions()")

        val instant = body(service, "internal fun tryInstantVideoSwap(): Boolean {", "    }")
        assertAfter(instant, "player = pre", "syncAudioEffectSessions()")
        assertAfter(instant, "runCatching { old.release() }", "syncAudioEffectSessions()")

        assertTrue(
            "media3 generates the session id in the background: announce it when it arrives",
            body(service, "override fun onAudioSessionIdChanged(audioSessionId: Int) {", "    }")
                .contains("syncAudioEffectSessions()"),
        )
    }

    @Test
    fun `OPEN and CLOSE are only sent through the session bookkeeping`() {
        val sync = body(service, "internal fun syncAudioEffectSessions() {", "    }")
        assertTrue(sync.contains("liveSessionId = if (isAudioEffectSessionOpened) player.audioSessionId else C.AUDIO_SESSION_ID_UNSET"))
        assertTrue(sync.contains("fadingSessionId = fadingPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET"))
        // The old one-shot broadcast named the live player's session directly; that is what lost it at each swap.
        assertFalse(service.contains("putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)"))
        assertEquals(2, Regex("ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION").findAll(service).count())
        assertFalse(crossfade.contains("ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION"))
    }

    @Test
    fun `the service closes every announced session when it is destroyed`() {
        val destroy = body(service, "override fun onDestroy() {", "    }")
        assertAfter(destroy, "closeAllAudioEffectSessions()", "player.release()")
        val closeAll = body(service, "private fun closeAllAudioEffectSessions() {", "    }")
        assertTrue(closeAll.contains("AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION"))
        assertTrue(closeAll.contains("announcedEffectSessions.clear()"))
    }
}
