package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.audio.EqBypass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the equalizer off must be audible.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cuando desactivo el ecualizador no siento ningún cambio"*. The native
 * chain gates only the bands and the de-esser on the master switch; spatial, the Tidal sound signature,
 * stereo width and the glue compressor kept running, and those are the stages that colour the sound most.
 * So the switch removed the bands and left the colour — no audible change, exactly as reported.
 *
 * The danger in the fix is the same shape as the bug: four call sites, one policy, and no compiler
 * anywhere checking that they agree. These tests hold the policy itself.
 */
class EqBypassTest {
    /** Everything a user could have switched on, so a bypass has the most to silence. */
    private fun allOn(masterEqOn: Boolean) =
        EqBypass.toneStages(
            masterEqOn = masterEqOn,
            spatialEnabled = true,
            tidalSignatureEnabled = true,
            stereoWidth = 1.6f,
            glueCompressorEnabled = true,
        )

    @Test
    fun `with the equalizer off every tone stage is bypassed`() {
        val off = allOn(masterEqOn = false)

        assertFalse("spatial kept playing with the EQ off", off.spatial)
        assertFalse("the Tidal signature kept playing with the EQ off", off.tidalSignature)
        assertFalse("the glue compressor kept playing with the EQ off", off.glueCompressor)
        assertEquals(
            "stereo width must return to neutral, not stay widened",
            EqBypass.NEUTRAL_STEREO_WIDTH,
            off.stereoWidth,
            0.0001f,
        )
    }

    @Test
    fun `with the equalizer on the user's choices are published untouched`() {
        val on = allOn(masterEqOn = true)

        assertTrue(on.spatial)
        assertTrue(on.tidalSignature)
        assertTrue(on.glueCompressor)
        assertEquals(1.6f, on.stereoWidth, 0.0001f)
    }

    @Test
    fun `the switch never turns a stage ON that the user had off`() {
        // The master switch may only ever subtract. A stage the user disabled must stay disabled.
        val stages = EqBypass.toneStages(
            masterEqOn = true,
            spatialEnabled = false,
            tidalSignatureEnabled = false,
            stereoWidth = EqBypass.NEUTRAL_STEREO_WIDTH,
            glueCompressorEnabled = false,
        )

        assertFalse(stages.spatial)
        assertFalse(stages.tidalSignature)
        assertFalse(stages.glueCompressor)
        assertEquals(EqBypass.NEUTRAL_STEREO_WIDTH, stages.stereoWidth, 0.0001f)
    }

    @Test
    fun `a narrowed stereo image is also returned to neutral, not merely capped`() {
        // < 1.0 narrows the sides. Bypass means "as it came in", in both directions.
        val off = EqBypass.toneStages(
            masterEqOn = false,
            spatialEnabled = false,
            tidalSignatureEnabled = false,
            stereoWidth = 0.4f,
            glueCompressorEnabled = false,
        )

        assertEquals(EqBypass.NEUTRAL_STEREO_WIDTH, off.stereoWidth, 0.0001f)
    }

    @Test
    fun `the bypass is total - no combination of user settings survives it`() {
        // Brute force over every on/off combination plus a spread of widths: with the master switch off,
        // the published stages must be identical to "nothing at all", whatever was stored.
        val widths = listOf(0f, 0.4f, 1f, 1.6f, 2f)
        for (spatial in listOf(false, true)) {
            for (tidal in listOf(false, true)) {
                for (comp in listOf(false, true)) {
                    for (w in widths) {
                        val off = EqBypass.toneStages(false, spatial, tidal, w, comp)
                        assertEquals(
                            "spatial=$spatial tidal=$tidal comp=$comp width=$w leaked past the bypass",
                            EqBypass.ToneStages(false, false, EqBypass.NEUTRAL_STEREO_WIDTH, false),
                            off,
                        )
                    }
                }
            }
        }
    }
}
