package iad1tya.echo.music.playback.audio

/**
 * Row 723 — which audio sessions the external equalizers (system EQ, Wavelet, JamesDSP…) must be told about, as a
 * pure decision. Those apps attach their effects to the session announced with
 * `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`. Every crossfade and the instant video swap make another
 * ExoPlayer — with its own session — the live one, so a single OPEN at the first play left them working on a
 * session nobody plays through any more.
 *
 *  - the LIVE player's session is announced while effects are open, and only once (no duplicate OPEN);
 *  - the OUTGOING crossfade player's session stays announced until that player is released: it is still audible
 *    for the whole fade;
 *  - every other announced session is closed.
 *
 * A session id <= 0 (media3 has not generated it yet) is never announced.
 */
internal object AudioEffectSessionPlan {

    /** What to broadcast now: CLOSE each session in [close] first, then OPEN [open] when it is not null. */
    data class Step(val close: List<Int>, val open: Int?)

    /**
     * @param announced sessions already announced with OPEN and not closed since.
     * @param liveSessionId the live player's session, or 0 while effects are closed (paused, stopped).
     * @param fadingSessionId the outgoing crossfade player's session, or 0 when no fade is running.
     */
    fun next(announced: Set<Int>, liveSessionId: Int, fadingSessionId: Int): Step {
        val close = announced.filter { it != liveSessionId && it != fadingSessionId }
        val open = liveSessionId.takeIf { it > 0 && it !in announced }
        return Step(close, open)
    }
}
