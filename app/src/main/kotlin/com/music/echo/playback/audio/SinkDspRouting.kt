package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util

/**
 * "Where does the EQ chain run for THIS sink configuration?", as a pure function.
 *
 * Lifted out of the anonymous `ForwardingAudioSink` in `MusicService.createRenderersFactory` for the
 * same reason as [AudioOffloadGate]: a decision that can silence the EQ, break Tempo & pitch or kill
 * the crossfade's tail detection has to be unit-testable, and nothing inside MusicService is.
 *
 * WHAT media3 1.11.1 DOES ON ITS OWN (DefaultAudioSink.configure, lines 753-760): the pipeline is
 * `[trimming, channelMapping]` and then EITHER `toFloatPcm` alone (when `shouldUseFloatOutput`, i.e.
 * float output enabled AND `Util.isEncodingHighResolutionPcm`) OR `toInt16Pcm + the custom chain`.
 * So the custom chain (silence detector, Superpowered EQ, …) only ever runs on 16-bit, and on the
 * float branch media3 also forces tempo/pitch back to 1.0 (`shouldApplyAudioProcessorPlaybackParameters`,
 * lines 1734-1745: "SonicAudioProcessor outputs 16-bit integer PCM").
 *
 * THE ROUTES
 *  - [Route.FLOAT_TAKEOVER_HIRES]: the 0.6.142 hi-res rescue. The predicate is EXACTLY the one that
 *    shipped (`!lowEnd && raw && highRes && no channel map`) and deliberately does NOT read the new
 *    setting, the playback parameters or the Listen Together room — hi-res behaviour is unchanged.
 *  - [Route.FLOAT_TAKEOVER_16BIT] (plan A3, opt-in, default OFF): the same takeover for little-endian
 *    16-bit PCM (what a platform Opus/AAC/MP3 decoder emits when it ignores media3's float request).
 *    Only while the user enabled it, no Listen Together room is live (the guest time-stretches with
 *    speed nudges, rows 261/262) and tempo/pitch are at 1.0 — because media3's float branch drops
 *    speed and pitch, and Sonic only exists on the int16 chain.
 *  - [Route.DELEGATE_INT16_CHAIN]: media3's own int16 pipeline runs the custom chain (today's path for
 *    all 16-bit content, and for everything on low-end devices).
 *  - [Route.DELEGATE_FLOAT_NO_DSP]: media3 takes its float branch and NOTHING of ours runs in it —
 *    hi-res with a channel map. Pre-existing and untouched; named so the log stops calling it int16.
 *  - [Route.PASSTHROUGH]: encoded passthrough/offload — no PCM, no chain at all.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object SinkDspRouting {

    enum class Route {
        PASSTHROUGH,
        DELEGATE_INT16_CHAIN,
        DELEGATE_FLOAT_NO_DSP,
        FLOAT_TAKEOVER_HIRES,
        FLOAT_TAKEOVER_16BIT,
    }

    /**
     * @param isRaw `MimeTypes.AUDIO_RAW == format.sampleMimeType`.
     * @param pcmEncoding the sink's INPUT encoding (`format.pcmEncoding`).
     * @param lowEnd mirrors `setEnableFloatOutput(!lowEnd)` on the delegate.
     * @param hasOutputChannelMapping `AudioSinkConfig.outputChannelMapping != null`.
     * @param float16Allowed the A3 setting AND "not in a Listen Together room", read by the caller.
     * @param speed / [pitch] the playback parameters the sink was last asked for.
     */
    fun route(
        isRaw: Boolean,
        pcmEncoding: Int,
        lowEnd: Boolean,
        hasOutputChannelMapping: Boolean,
        float16Allowed: Boolean,
        speed: Float,
        pitch: Float,
    ): Route {
        if (!isRaw) return Route.PASSTHROUGH
        val highRes = Util.isEncodingHighResolutionPcm(pcmEncoding)
        // Byte-identical to the predicate that shipped in createRenderersFactory before A3.
        if (!lowEnd && highRes && !hasOutputChannelMapping) return Route.FLOAT_TAKEOVER_HIRES
        // media3 itself takes the float branch here and skips the chain (pre-existing, unchanged).
        if (!lowEnd && highRes) return Route.DELEGATE_FLOAT_NO_DSP
        // EXACTLY little-endian 16-bit: 8-bit and big-endian 16-bit stay on media3's proven path.
        if (!lowEnd &&
            pcmEncoding == C.ENCODING_PCM_16BIT &&
            !hasOutputChannelMapping &&
            float16Allowed &&
            isDefaultParameters(speed, pitch)
        ) {
            return Route.FLOAT_TAKEOVER_16BIT
        }
        return Route.DELEGATE_INT16_CHAIN
    }

    /**
     * True when a playback-parameter change must hand a 16-bit takeover back to media3's int16 chain
     * (the only place Sonic can apply it). Never for the hi-res route: that would silently requantize
     * 24-bit content to 16 bits, and hi-res keeps exactly its pre-A3 behaviour.
     */
    fun shouldRevert(route: Route, speed: Float, pitch: Float): Boolean =
        route == Route.FLOAT_TAKEOVER_16BIT && !isDefaultParameters(speed, pitch)

    /**
     * True when the delegate will NOT run the custom chain for raw PCM, so the silence detector has to
     * be fed by the sink-level tap instead (`SilenceDetectorAudioProcessor.externallyFed`).
     */
    fun chainBypassedByDelegate(route: Route): Boolean = when (route) {
        Route.FLOAT_TAKEOVER_HIRES,
        Route.FLOAT_TAKEOVER_16BIT,
        Route.DELEGATE_FLOAT_NO_DSP -> true
        Route.DELEGATE_INT16_CHAIN,
        Route.PASSTHROUGH -> false
    }

    /** True for the two routes where this sink drives the EQ chain itself, in float. */
    fun isTakeover(route: Route): Boolean =
        route == Route.FLOAT_TAKEOVER_HIRES || route == Route.FLOAT_TAKEOVER_16BIT

    /** Same comparison as `PlaybackParameters.equals` against `PlaybackParameters.DEFAULT`. */
    fun isDefaultParameters(speed: Float, pitch: Float): Boolean = speed == 1f && pitch == 1f
}
