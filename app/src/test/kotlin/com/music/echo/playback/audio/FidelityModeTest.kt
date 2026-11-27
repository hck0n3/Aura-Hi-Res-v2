package iad1tya.echo.music.playback.audio

import iad1tya.echo.music.playback.audio.FidelityMode.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 902 — «Modo Fidelidad»: the gating table. Each wrong cell fails silently (only sound): a stage left on
 * is a «bit-perfect» switch that is not, a protection dropped is a speaker the user did not agree to risk.
 */
class FidelityModeTest {

    @Test
    fun `with the mode off every stage runs exactly as the user set it`() {
        for (stage in Stage.values()) {
            assertFalse(stage.name, FidelityMode.isBypassed(stage, fidelityOn = false))
            assertTrue(stage.name, FidelityMode.effective(stage, userEnabled = true, fidelityOn = false))
            assertFalse(stage.name, FidelityMode.effective(stage, userEnabled = false, fidelityOn = false))
        }
        assertEquals(1.3f, FidelityMode.effectiveStereoWidth(1.3f, fidelityOn = false), 0f)
    }

    @Test
    fun `with the mode on every sample-changing stage is bypassed`() {
        val expected = setOf(
            Stage.EQUALIZER, Stage.SAFE_VOLUME, Stage.SPATIAL, Stage.TIDAL_SIGNATURE, Stage.DE_ESSER,
            Stage.GLUE_COMPRESSOR, Stage.OUTPUT_DITHER, Stage.STEREO_WIDTH, Stage.CROSSFADE, Stage.MANUAL_FADE,
            Stage.TEMPO_PITCH, Stage.FLOAT_16BIT_TAKEOVER,
            // Row 920: the psychoacoustic bass rewrites the bass.
            Stage.VIRTUAL_BASS,
        )
        assertEquals(expected, FidelityMode.BYPASSED)
        for (stage in expected) {
            assertTrue(stage.name, FidelityMode.isBypassed(stage, fidelityOn = true))
            assertFalse(stage.name, FidelityMode.effective(stage, userEnabled = true, fidelityOn = true))
        }
        assertEquals(1f, FidelityMode.effectiveStereoWidth(1.3f, fidelityOn = true), 0f)
        assertEquals(1f, FidelityMode.effectiveStereoWidth(0.5f, fidelityOn = true), 0f)
    }

    @Test
    fun `the speaker protection and the 32-bit float output are kept on purpose`() {
        assertEquals(setOf(Stage.SPEAKER_BASS_PROTECTION, Stage.FLOAT_OUTPUT), FidelityMode.KEPT)
        for (stage in FidelityMode.KEPT) {
            assertFalse(stage.name, FidelityMode.isBypassed(stage, fidelityOn = true))
            assertTrue(stage.name, FidelityMode.effective(stage, userEnabled = true, fidelityOn = true))
        }
    }

    @Test
    fun `every stage is either bypassed or kept, never forgotten`() {
        assertEquals(Stage.values().toSet(), FidelityMode.BYPASSED + FidelityMode.KEPT)
        assertTrue((FidelityMode.BYPASSED intersect FidelityMode.KEPT).isEmpty())
    }

    @Test
    fun `tempo and pitch go back to 1_0 only with the mode on`() {
        assertTrue(FidelityMode.mustResetPlaybackParameters(true, 1.25f, 1f, inListenTogetherRoom = false))
        assertTrue(FidelityMode.mustResetPlaybackParameters(true, 1f, 1.0594631f, inListenTogetherRoom = false))
        // Already at 1.0: nothing to do (the reset itself re-enters the callback with DEFAULT).
        assertFalse(FidelityMode.mustResetPlaybackParameters(true, 1f, 1f, inListenTogetherRoom = false))
        // A room keeps in time by nudging the speed: never fought.
        assertFalse(FidelityMode.mustResetPlaybackParameters(true, 1.02f, 1f, inListenTogetherRoom = true))
        // …but the pitch is: nothing in a room needs it.
        assertTrue(FidelityMode.mustResetPlaybackParameters(true, 1.02f, 1.122462f, inListenTogetherRoom = true))
        // Mode off: the user's tempo stays.
        assertFalse(FidelityMode.mustResetPlaybackParameters(false, 1.25f, 0.9f, inListenTogetherRoom = false))
    }

    @Test
    fun `turning the mode off gives back the tempo and pitch it reset`() {
        assertEquals(1.25f to 1.122462f, FidelityMode.restoredPlaybackParameters(1.25f, 1.122462f, false, 1f, false))
        // Back inside a room now: the room's current speed stays, the pitch comes back.
        assertEquals(0.98f to 1.122462f, FidelityMode.restoredPlaybackParameters(1.25f, 1.122462f, false, 0.98f, true))
        // Saved inside a room: that speed was the room's nudge, never the user's.
        assertEquals(1f to 1.122462f, FidelityMode.restoredPlaybackParameters(1.02f, 1.122462f, true, 1f, false))
    }

    @Test
    fun `in a room only the pitch goes back to 1_0 and the room's speed is kept`() {
        assertEquals(1.02f to 1f, FidelityMode.allowedPlaybackParameters(true, 1.02f, 1.122462f, inListenTogetherRoom = true))
        assertEquals(null, FidelityMode.allowedPlaybackParameters(true, 0.98f, 1f, inListenTogetherRoom = true))
        // Outside a room both go back.
        assertEquals(1f to 1f, FidelityMode.allowedPlaybackParameters(true, 1.25f, 1.122462f, inListenTogetherRoom = false))
        assertEquals(null, FidelityMode.allowedPlaybackParameters(true, 1f, 1f, inListenTogetherRoom = false))
        // Mode off: nothing changes, in or out of a room.
        assertEquals(null, FidelityMode.allowedPlaybackParameters(false, 1.25f, 1.122462f, inListenTogetherRoom = false))
        assertEquals(null, FidelityMode.allowedPlaybackParameters(false, 1.02f, 1.122462f, inListenTogetherRoom = true))
    }
}
