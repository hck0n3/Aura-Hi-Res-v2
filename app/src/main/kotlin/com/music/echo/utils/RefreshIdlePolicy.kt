package iad1tya.echo.music.utils

import kotlin.math.abs

/**
 * Registry row 899 (plan phase 7): «refresco alto al interactuar y bajo en reposo».
 *
 * The window asks for the panel's high rate only while the user is touching, scrolling or pressing keys (and for
 * [IDLE_MS] after the last input, which covers a fling and the animation a tap starts); at rest it asks for 60 Hz, or
 * the lowest standard mode at or above 60 Hz the panel has. The «alta tasa de refresco» switch still wins (off = 60 Hz
 * always) and so do battery saver and heat ([RefreshRatePlanning.ceilingHz]).
 *
 * Event-driven only (AGENTS rule 7): there is no timer while idle. Every input re-arms ONE delayed callback; when it
 * fires the policy goes to rest and nothing else is scheduled until the next input.
 *
 * Pure state machine (no Android types) so it is unit-tested; MainActivity owns the single delayed callback.
 */
class RefreshIdlePolicy {
    /** Interacting (or within [IDLE_MS] of the last input). Starts true: opening the app is input. */
    var active: Boolean = true
        private set

    private var touching = false

    /** A finger went down. Returns true when [active] changed and the window's request must be re-evaluated. */
    fun onTouchDown(): Boolean {
        touching = true
        return activate()
    }

    /** The gesture ended (last finger up, or cancelled). The caller re-arms the idle callback. */
    fun onTouchEnd(): Boolean {
        touching = false
        return activate()
    }

    /** A key, D-pad, wheel or trackball event, or the window coming back. The caller re-arms the idle callback. */
    fun onOtherInput(): Boolean = activate()

    /** The idle callback fired. While a finger is still down nothing changes: lifting it re-arms the callback. */
    fun onIdleTimeout(): Boolean {
        if (touching || !active) return false
        active = false
        return true
    }

    /** The window left the screen: no finger can still be down on it. */
    fun onHidden() {
        touching = false
    }

    private fun activate(): Boolean {
        if (active) return false
        active = true
        return true
    }

    companion object {
        /** Chosen for this change, not measured: long enough for a fling or a tap's animation to finish. */
        const val IDLE_MS = 3_000L

        /** The rate asked for at rest. */
        const val REST_HZ = 60f

        /** The ceiling to ask for: the device's own (heat, battery saver) while active, at most [REST_HZ] at rest. */
        fun ceilingHz(active: Boolean, systemCeilingHz: Float): Float =
            if (active) systemCeilingHz else minOf(systemCeilingHz, REST_HZ)

        /** At rest, never below [REST_HZ] when the panel has such a mode (a 50 Hz mode is not «60 Hz or the lowest ≥ 60»). */
        fun floorHz(active: Boolean): Float = if (active) 0f else REST_HZ

        /**
         * Whether going from [fromHz] to [toHz] keeps the picture on screen. [alternatives] is the current mode's
         * `Display.Mode.getAlternativeRefreshRates()` (API 31+), the rates it can switch to seamlessly; null when the
         * platform cannot say (below API 31), which keeps the previous behaviour.
         */
        fun seamless(fromHz: Float, toHz: Float, alternatives: List<Float>?): Boolean =
            abs(fromHz - toHz) < 0.5f || alternatives == null || alternatives.any { abs(it - toHz) < 0.5f }
    }
}
