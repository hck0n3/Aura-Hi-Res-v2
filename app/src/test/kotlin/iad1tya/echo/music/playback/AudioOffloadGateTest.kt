package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.audio.AudioOffloadGate
import iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason
import iad1tya.echo.music.playback.audio.AudioOffloadGate.Inputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the offload gate. The failure this guards against is not a crash: it is offload engaging while
 * a DSP stage is live, which makes that stage INAUDIBLE while its screen keeps claiming it is on. The
 * asymmetry is deliberate — a false "blocked" only costs battery, a false "allowed" costs audio.
 */
class AudioOffloadGateTest {

    /** Everything off / cleared: the only genuinely bit-perfect configuration. */
    private val passthrough = Inputs(
        userWantsOffload = true,
        crossfadeEnabled = false,
        highPerformanceMode = false,
        safeVolumeEnabled = false,
        equalizerActive = false,
    )

    @Test
    fun `offload is allowed only on true passthrough`() {
        assertNull(AudioOffloadGate.blockReason(passthrough))
        assertTrue(AudioOffloadGate.allowOffload(passthrough))
    }

    @Test
    fun `the user switch alone never grants offload`() {
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(userWantsOffload = false)))
        // ...and the switch being on is not enough either, once anything is live.
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(equalizerActive = true)))
    }

    @Test
    fun `every live DSP stage vetoes offload`() {
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(crossfadeEnabled = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(safeVolumeEnabled = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(equalizerActive = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(spatialEnabled = true)))
        assertEquals(BlockReason.SPATIAL, AudioOffloadGate.blockReason(passthrough.copy(spatialEnabled = true)))
    }

    /**
     * Una sala de Escuchar juntos también veta, y no por DSP.
     *
     * 🔴 (2026-09-17) El invitado se alinea con el anfitrión ajustando la VELOCIDAD en vez de saltar
     * (`SyncCorrection`). Una velocidad distinta de 1.0 hace que `setOffloadEnabled` pida soporte de
     * cambio de velocidad, y en un teléfono cuyo HAL no lo tenga eso es una reselección de pista —
     * audible — en cada cruce entre "corrigiendo" y "ya alineado", varias veces por canción. Vetarlo
     * mientras dura la sala cambia esas muchas reconfiguraciones por una sola al entrar.
     *
     * Va el ÚLTIMO en el orden de motivos a propósito: ponerlo antes cambiaría el motivo que ya se
     * muestra hoy para configuraciones que no han cambiado, y por eso se comprueba que una sala
     * sobre una cadena limpia da LISTEN_TOGETHER mientras que una sala con el EQ puesto sigue
     * diciendo EQUALIZER.
     */
    @Test
    fun `una sala de escuchar juntos veta el offload sin robarle el motivo a las demas`() {
        val enSala = passthrough.copy(listenTogetherActive = true)
        assertFalse(AudioOffloadGate.allowOffload(enSala))
        assertEquals(BlockReason.LISTEN_TOGETHER, AudioOffloadGate.blockReason(enSala))
        assertEquals(
            BlockReason.EQUALIZER,
            AudioOffloadGate.blockReason(enSala.copy(equalizerActive = true)),
        )
        // Y fuera de la sala no cambia nada de lo de antes.
        assertEquals(null, AudioOffloadGate.blockReason(passthrough))
    }

    /**
     * Row 714 — the glue compressor, phone-speaker protection and a stereo width other than 100 % live in
     * Ajustes ▸ Sonido and process PCM whatever the equalizer switch says, so each vetoes on its own. Its
     * reason is appended last: a configuration that already had a reason keeps it.
     */
    @Test
    fun `an active mastering stage vetoes offload with its own reason`() {
        val mastering = passthrough.copy(masteringActive = true)
        assertFalse(AudioOffloadGate.allowOffload(mastering))
        assertEquals(BlockReason.MASTERING, AudioOffloadGate.blockReason(mastering))
        assertEquals(BlockReason.EQUALIZER, AudioOffloadGate.blockReason(mastering.copy(equalizerActive = true)))
        assertEquals(
            BlockReason.LISTEN_TOGETHER,
            AudioOffloadGate.blockReason(mastering.copy(listenTogetherActive = true)),
        )
    }

    /**
     * Row 714 — spatial audio and the Tidal signature live inside the equalizer: with its switch off
     * EqBypass.toneStages publishes both in bypass, so their stored switches must not cost offload. The
     * Tidal signature defaults ON, so before this every user with the EQ off was vetoed for a stage that
     * was not running.
     */
    @Test
    fun `spatial and Tidal with the EQ off do not veto`() {
        assertFalse(AudioOffloadGate.eqToneStagesLive(equalizerActive = false, spatialEnabled = true, tidalSignatureEnabled = true))
        val eqOffToneOn = passthrough.copy(
            spatialEnabled = AudioOffloadGate.eqToneStagesLive(
                equalizerActive = false,
                spatialEnabled = true,
                tidalSignatureEnabled = true,
            ),
        )
        assertNull(AudioOffloadGate.blockReason(eqOffToneOn))
        assertTrue(AudioOffloadGate.allowOffload(eqOffToneOn))

        // With the EQ on they are live (and the EQ itself already vetoes).
        assertTrue(AudioOffloadGate.eqToneStagesLive(equalizerActive = true, spatialEnabled = true, tidalSignatureEnabled = false))
        assertTrue(AudioOffloadGate.eqToneStagesLive(equalizerActive = true, spatialEnabled = false, tidalSignatureEnabled = true))
        assertFalse(AudioOffloadGate.eqToneStagesLive(equalizerActive = true, spatialEnabled = false, tidalSignatureEnabled = false))
    }

    /**
     * The EQ term is "a profile is applied", not "the profile is audibly non-flat", because the preamp
     * and the de-esser both run inside the native runEq branch. A flat profile still processes.
     */
    @Test
    fun `an applied EQ profile vetoes even when the caller believes it is flat`() {
        assertEquals(
            BlockReason.EQUALIZER,
            AudioOffloadGate.blockReason(passthrough.copy(equalizerActive = true)),
        )
    }

    @Test
    fun `crossfade only vetoes when it can actually run`() {
        val crossfadeOn = passthrough.copy(crossfadeEnabled = true)
        assertTrue(AudioOffloadGate.crossfadeRunning(crossfadeOn))
        assertEquals(BlockReason.CROSSFADE, AudioOffloadGate.blockReason(crossfadeOn))

        // High-Performance Mode force-disables crossfade at every live site, so no second player is ever
        // built and the key alone must NOT cost offload on exactly the weak devices that need it most.
        val underPerfMode = crossfadeOn.copy(highPerformanceMode = true)
        assertFalse(AudioOffloadGate.crossfadeRunning(underPerfMode))
        assertNull(AudioOffloadGate.blockReason(underPerfMode))
        assertTrue(AudioOffloadGate.allowOffload(underPerfMode))
    }

    @Test
    fun `perf mode does not rescue offload from the other vetoes`() {
        val perfModeWithEq = passthrough.copy(highPerformanceMode = true, equalizerActive = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithEq))
        val perfModeWithSafeVolume = passthrough.copy(highPerformanceMode = true, safeVolumeEnabled = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithSafeVolume))
        val perfModeWithSpatial = passthrough.copy(highPerformanceMode = true, spatialEnabled = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithSpatial))
    }

    /**
     * The owner's own defaults: crossfade on, Safe Volume on, an EQ profile applied. This test exists so
     * nobody reads the feature as a battery win for him — it is not, and it must never silently become
     * one by relaxing a term.
     */
    @Test
    fun `the shipped defaults never reach offload`() {
        val ownerDefaults = Inputs(
            userWantsOffload = true,
            crossfadeEnabled = true,
            highPerformanceMode = false,
            safeVolumeEnabled = true,
            equalizerActive = true,
        )
        assertFalse(AudioOffloadGate.allowOffload(ownerDefaults))
        assertEquals(BlockReason.CROSSFADE, AudioOffloadGate.blockReason(ownerDefaults))
    }

    /**
     * Exhaustive: across every combination of the four DSP inputs, "allowed" must imply that all of them
     * are idle. This is the invariant, not the individual cases above.
     */
    @Test
    fun `allowed implies nothing is live, over the whole input space`() {
        val flags = listOf(false, true)
        var allowedCount = 0
        for (wants in flags) for (cf in flags) for (perf in flags) for (sv in flags) for (eq in flags) for (spatial in flags) for (mastering in flags) {
            val inputs = Inputs(wants, cf, perf, sv, eq, spatial, masteringActive = mastering)
            if (AudioOffloadGate.allowOffload(inputs)) {
                allowedCount++
                assertTrue("offload allowed without the user asking: $inputs", inputs.userWantsOffload)
                assertFalse("offload allowed with Safe Volume live: $inputs", inputs.safeVolumeEnabled)
                assertFalse("offload allowed with the EQ live: $inputs", inputs.equalizerActive)
                assertFalse("offload allowed with spatial live: $inputs", inputs.spatialEnabled)
                assertFalse("offload allowed with a mastering stage live: $inputs", inputs.masteringActive)
                assertFalse(
                    "offload allowed with crossfade able to run: $inputs",
                    inputs.crossfadeEnabled && !inputs.highPerformanceMode,
                )
            }
        }
        // wants=true, sv=false, eq=false, spatial=false, mastering=false, and (cf=false with perf either way) or
        // (cf=true with perf=true).
        assertEquals(3, allowedCount)
    }
}
