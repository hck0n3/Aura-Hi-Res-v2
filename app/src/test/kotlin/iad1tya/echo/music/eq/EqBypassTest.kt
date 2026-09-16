package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.audio.EqBypass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the equalizer off must be audible — and must not reach past the equalizer.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cuando desactivo el ecualizador no siento ningún cambio"*. The native
 * chain gates only the bands and the de-esser on the master switch, so the stages that colour the sound
 * kept running and the switch did nothing audible.
 *
 * 🔴 AND THE CORRECTION, the same day: *"la función ancho de estéreo no escucho ningún cambio cuando la
 * uso"*. The first fix reached too far — it also bypassed the stereo width and the glue compressor, which
 * are NOT inside the equalizer: they live in Ajustes ▸ Sonido ▸ Masterización, each with its own control,
 * on a screen that says nothing about the equalizer. With the equalizer off, his slider had been made
 * silently inert. The fix for a silent-inert-control bug had created one.
 *
 * Both halves are pinned here, because the second is the easier one to lose: it is invisible from this
 * file's side (a stage simply stops being mentioned) and nothing else would fail.
 */
class EqBypassTest {
    @Test
    fun `with the equalizer off its own stages are bypassed`() {
        val off = EqBypass.toneStages(masterEqOn = false, spatialEnabled = true, tidalSignatureEnabled = true)

        assertFalse("spatial kept playing with the EQ off", off.spatial)
        assertFalse("the Tidal signature kept playing with the EQ off", off.tidalSignature)
    }

    @Test
    fun `with the equalizer on the user's choices are published untouched`() {
        val on = EqBypass.toneStages(masterEqOn = true, spatialEnabled = true, tidalSignatureEnabled = true)

        assertTrue(on.spatial)
        assertTrue(on.tidalSignature)
    }

    @Test
    fun `the switch never turns a stage ON that the user had off`() {
        // The master switch may only ever subtract.
        val stages = EqBypass.toneStages(masterEqOn = true, spatialEnabled = false, tidalSignatureEnabled = false)

        assertFalse(stages.spatial)
        assertFalse(stages.tidalSignature)
    }

    @Test
    fun `the equalizer switch has no say over the Sound screen's controls`() {
        // THE CORRECTION, pinned by shape rather than by value: [EqBypass.ToneStages] must carry ONLY the
        // stages the equalizer screen presents. Stereo width, the glue compressor, output dither, speaker
        // bass protection and Safe Volume live on other screens and must never be decided here — the day
        // one of them is added back to this type, the owner's slider goes quietly dead again.
        // INSTANCE fields only. The Compose compiler adds a static `$stable` to classes in this module,
        // and it is NOT marked synthetic — the first version of this test counted it as a governed stage
        // and failed in CI on a correct implementation. Statics are compiler bookkeeping, never state.
        val fields = EqBypass.ToneStages::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filterNot { it.name.startsWith("$") }
            .map { it.name }
            .sorted()

        assertEquals(
            "EqBypass.ToneStages may only govern the stages the equalizer SCREEN owns. Anything else " +
                "belongs to its own screen's control: gating it here makes that control silently inert, " +
                "which is the exact bug this class was written to remove.",
            listOf("spatial", "tidalSignature"),
            fields,
        )
    }

    @Test
    fun `the bypass is total for the stages it does own`() {
        for (spatial in listOf(false, true)) {
            for (tidal in listOf(false, true)) {
                assertEquals(
                    "spatial=$spatial tidal=$tidal leaked past the bypass",
                    EqBypass.ToneStages(spatial = false, tidalSignature = false),
                    EqBypass.toneStages(masterEqOn = false, spatialEnabled = spatial, tidalSignatureEnabled = tidal),
                )
            }
        }
    }
}
