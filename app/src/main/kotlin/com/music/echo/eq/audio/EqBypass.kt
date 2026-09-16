package iad1tya.echo.music.eq.audio

/**
 * What the master equalizer switch actually switches.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cuando desactivo el ecualizador no siento ningún cambio"* → *"quiero que
 * desactive todo lo que está dentro del ecualizador"*.
 *
 * The native chain gates only the bands and the de-esser on that switch (`if (runEq)` in
 * SuperpoweredBridge.cpp). Spatial, the Tidal sound signature, stereo width and the glue compressor kept
 * running — `applySpatial` even documented it as deliberate. Those are the stages that colour the sound
 * most, so switching the equalizer off took away the bands and left the colour behind, and nothing audible
 * changed.
 *
 * This is the POLICY, kept apart from JNI so it can be tested and, more importantly, so it is written down
 * in one place instead of implied by four call sites: which stages the switch owns, and which it must never
 * touch.
 */
object EqBypass {
    /** Exactly what gets published to the native processor for the four tone stages. */
    data class ToneStages(
        val spatial: Boolean,
        val tidalSignature: Boolean,
        /** 1.0 is untouched mid/side — the bypass value. */
        val stereoWidth: Float,
        val glueCompressor: Boolean,
    )

    /** Stereo width that changes nothing. */
    const val NEUTRAL_STEREO_WIDTH = 1f

    /**
     * The tone stages to publish.
     *
     * With [masterEqOn] false every one of them is bypassed, whatever the user's stored choices are —
     * those choices are kept elsewhere and come back untouched when the equalizer is switched on again.
     *
     * NOT here, on purpose: Safe Volume, the true-peak limiter, output dither and phone-speaker bass
     * protection. They protect the listener and the hardware instead of shaping tone, so the equalizer
     * switch must not be able to make the app suddenly louder or expose a phone speaker to unprotected
     * sub-bass. Safe Volume even lives on a different screen (Ajustes ▸ Reproductor). If a future stage is
     * added, it belongs here only if turning it off is a matter of taste rather than of safety.
     */
    fun toneStages(
        masterEqOn: Boolean,
        spatialEnabled: Boolean,
        tidalSignatureEnabled: Boolean,
        stereoWidth: Float,
        glueCompressorEnabled: Boolean,
    ): ToneStages =
        ToneStages(
            spatial = masterEqOn && spatialEnabled,
            tidalSignature = masterEqOn && tidalSignatureEnabled,
            stereoWidth = if (masterEqOn) stereoWidth else NEUTRAL_STEREO_WIDTH,
            glueCompressor = masterEqOn && glueCompressorEnabled,
        )
}
