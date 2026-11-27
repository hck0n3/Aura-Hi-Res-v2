package iad1tya.echo.music.playback.audio

/**
 * «Modo Fidelidad» (owner decision H17, `docs/plan/DECISIONES_2026-11-24.md`; registry row 902): which
 * stage the switch turns off so the samples reach the sink untouched, as a pure table that the service
 * and the screens both read.
 *
 * Everything here is bypassed FROM THE PLAYBACK SIDE ONLY (MusicService): the equalizer and the
 * Superpowered engine (`eq/`, `app/src/main/cpp/`) are not edited. With every stage below off the
 * native chain skips its DSP block entirely (`runChain == false` in `SuperpoweredBridge.processAudio`):
 * the same idle state [AudioOffloadGate] calls bit-perfect passthrough.
 *
 * Nothing the user chose is overwritten: each preference keeps its value and comes back the moment the
 * mode is turned off. The screens say «En pausa» next to each paused control (no hidden placebo).
 */
object FidelityMode {

    enum class Stage {
        /** Bands, preamp and de-esser (the equalizer profile). */
        EQUALIZER,
        /** Safe Volume: the per-track levelling (ReplayGain / loudness normalization) and its limiter. */
        SAFE_VOLUME,
        SPATIAL,
        TIDAL_SIGNATURE,
        DE_ESSER,
        GLUE_COMPRESSOR,
        /** Only ever acts on a 16-bit conversion the idle chain no longer makes. */
        OUTPUT_DITHER,
        STEREO_WIDTH,
        CROSSFADE,
        /** The short fade-in on a manual track change («Entrada suave al cambiar de canción»). */
        MANUAL_FADE,
        TEMPO_PITCH,
        /** Plan A3's 16-bit float takeover: kept 16-bit so the track matches a 16-bit bit-perfect mixer. */
        FLOAT_16BIT_TAKEOVER,
        /** Phone-speaker sub-bass protection: a hardware protection, armed only on the phone's own speaker. */
        SPEAKER_BASS_PROTECTION,
        /** 32-bit float output on mid/high-tier devices (`setEnableFloatOutput(!lowEnd)`). */
        FLOAT_OUTPUT,
        /** Row 918: «Graves y agudos a volumen bajo», the shelves that follow the system volume. */
        LOUDNESS_COMPENSATION,
    }

    /** What Fidelidad switches off. */
    val BYPASSED: Set<Stage> = setOf(
        Stage.EQUALIZER,
        Stage.SAFE_VOLUME,
        Stage.SPATIAL,
        Stage.TIDAL_SIGNATURE,
        Stage.DE_ESSER,
        Stage.GLUE_COMPRESSOR,
        Stage.OUTPUT_DITHER,
        Stage.STEREO_WIDTH,
        Stage.CROSSFADE,
        Stage.MANUAL_FADE,
        Stage.TEMPO_PITCH,
        Stage.FLOAT_16BIT_TAKEOVER,
        Stage.LOUDNESS_COMPENSATION,
    )

    /**
     * What Fidelidad keeps, on purpose: the speaker protection never runs on a DAC or headphones (so it
     * cannot touch a bit-perfect stream), and 32-bit float output is what carries Hi-Res untouched.
     */
    val KEPT: Set<Stage> = setOf(Stage.SPEAKER_BASS_PROTECTION, Stage.FLOAT_OUTPUT)

    fun isBypassed(stage: Stage, fidelityOn: Boolean): Boolean = fidelityOn && stage in BYPASSED

    /** The value a stage actually runs with: the user's own switch, unless Fidelidad pauses it. */
    fun effective(stage: Stage, userEnabled: Boolean, fidelityOn: Boolean): Boolean =
        userEnabled && !isBypassed(stage, fidelityOn)

    /** Stereo width is a ratio, not a switch: 1.0 is the untouched value. */
    fun effectiveStereoWidth(userWidth: Float, fidelityOn: Boolean): Float =
        if (isBypassed(Stage.STEREO_WIDTH, fidelityOn)) 1f else userWidth

    /**
     * Whether the service must change tempo or pitch: see [allowedPlaybackParameters].
     */
    fun mustResetPlaybackParameters(
        fidelityOn: Boolean,
        speed: Float,
        pitch: Float,
        inListenTogetherRoom: Boolean,
    ): Boolean = allowedPlaybackParameters(fidelityOn, speed, pitch, inListenTogetherRoom) != null

    /**
     * The (speed, pitch) the service must set, or null when the current ones may stay. Outside a room both
     * go back to 1.0. Inside a Listen Together room only the pitch does: the guest stays in time by nudging
     * the speed (rows 261/262/744), and breaking the room is worse than a few seconds of time-stretch, but
     * nothing in the room needs a pitch other than 1.0. When the room state cannot be read the caller
     * passes `true`.
     */
    fun allowedPlaybackParameters(
        fidelityOn: Boolean,
        speed: Float,
        pitch: Float,
        inListenTogetherRoom: Boolean,
    ): Pair<Float, Float>? {
        if (!isBypassed(Stage.TEMPO_PITCH, fidelityOn)) return null
        if (inListenTogetherRoom) return if (pitch == 1f) null else speed to 1f
        // Same comparison as SinkDspRouting.isDefaultParameters (PlaybackParameters.DEFAULT).
        return if (speed == 1f && pitch == 1f) null else 1f to 1f
    }

    /**
     * The (speed, pitch) to put back when the mode is turned off: what it first reset. A speed saved inside a
     * Listen Together room was the room's nudge, not the user's, so it is never put back; inside a room now,
     * the room's current speed stays.
     */
    fun restoredPlaybackParameters(
        savedSpeed: Float,
        savedPitch: Float,
        savedInRoom: Boolean,
        currentSpeed: Float,
        inListenTogetherRoom: Boolean,
    ): Pair<Float, Float> = (if (savedInRoom || inListenTogetherRoom) currentSpeed else savedSpeed) to savedPitch
}
