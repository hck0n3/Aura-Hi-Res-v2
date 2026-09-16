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
 *
 * ## Scope, corrected the same day
 * The first version of this also bypassed the stereo width and the glue compressor, and that was wrong.
 * The owner's words were "todo lo que está dentro del ecualizador", and those two are NOT inside it: they
 * live in Ajustes ▸ Sonido ▸ Masterización, each with its own control, on a screen that says nothing about
 * the equalizer. He found it immediately — *"la función ancho de estéreo no escucho ningún cambio cuando
 * la uso"* — because with the equalizer off his slider had been made silently inert. That is the exact bug
 * class this whole run has been removing, introduced by the fix for it.
 *
 * So the switch owns only what the equalizer SCREEN presents: its bands and preamp (handled natively), the
 * spatial stage and the Tidal signature, both of which are shown inside that screen. Everything on the
 * Sound screen keeps working on its own, equalizer or no equalizer.
 */
object EqBypass {
    /** Exactly what gets published to the native processor for the stages the equalizer switch owns. */
    data class ToneStages(
        val spatial: Boolean,
        val tidalSignature: Boolean,
    )

    /**
     * The tone stages to publish.
     *
     * With [masterEqOn] false every one of them is bypassed, whatever the user's stored choices are —
     * those choices are kept elsewhere and come back untouched when the equalizer is switched on again.
     *
     * NOT here, on purpose, and each for its own reason:
     *  - Stereo width and the glue compressor: they live on the SOUND screen, not inside the equalizer.
     *    A control must not be made inert by a switch on another screen.
     *  - Safe Volume, the true-peak limiter, output dither and phone-speaker bass protection: they protect
     *    the listener and the hardware instead of shaping tone, so the equalizer switch must not be able to
     *    make the app suddenly louder or expose a phone speaker to unprotected sub-bass.
     *
     * A future stage belongs here only if BOTH hold: the equalizer screen is where the user meets it, and
     * turning it off is a matter of taste rather than of safety.
     */
    fun toneStages(
        masterEqOn: Boolean,
        spatialEnabled: Boolean,
        tidalSignatureEnabled: Boolean,
    ): ToneStages =
        ToneStages(
            spatial = masterEqOn && spatialEnabled,
            tidalSignature = masterEqOn && tidalSignatureEnabled,
        )
}
