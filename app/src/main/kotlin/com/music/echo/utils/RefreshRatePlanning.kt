package iad1tya.echo.music.utils

/** One display mode as `Display.Mode` reports it, kept free of Android types so the choice below is unit-testable. */
data class RefreshMode(val id: Int, val width: Int, val height: Int, val hz: Float)

/**
 * Which refresh-rate mode the window should ask for.
 *
 * Owner 2026-10-15: «que la aplicación sea capaz de adaptarse a la tasa que tenga el celular en ese momento… para que
 * siempre esté fluida en celulares de gama alta y no se trabe». The window used to pin the panel's highest mode once and
 * re-assert it on a configuration change only. "At that moment" is wider than that: the user flips the system's
 * smoothness setting, battery saver switches on, the phone heats up, the fold changes panel. So the request is a pure
 * function of (modes, current mode, ceiling) and the ceiling follows the device's state:
 *
 *  · cool, no battery saver → no ceiling: the highest mode of the current resolution (what it always was, 120 Hz on a
 *    high-end phone);
 *  · THERMAL_STATUS_MODERATE (2) → at most 90 Hz, so a hot phone sheds frames before it throttles the whole SoC;
 *  · THERMAL_STATUS_SEVERE (3) or worse, or battery saver → 60 Hz.
 */
object RefreshRatePlanning {
    const val NO_CEILING = Float.MAX_VALUE
    private const val THERMAL_MODERATE = 2
    private const val THERMAL_SEVERE = 3

    fun ceilingHz(powerSave: Boolean, thermalStatus: Int): Float = when {
        powerSave || thermalStatus >= THERMAL_SEVERE -> 60f
        thermalStatus >= THERMAL_MODERATE -> 90f
        else -> NO_CEILING
    }

    /**
     * The best mode at the CURRENT resolution (a mode with another resolution would resize the panel's framebuffer):
     * the highest rate not above [ceilingHz]; when every mode is above it, the lowest there is. Falls back to every
     * mode if none shares the resolution. `null` only for an empty list.
     */
    fun pick(modes: List<RefreshMode>, current: RefreshMode, ceilingHz: Float): RefreshMode? {
        val sameSize = modes.filter { it.width == current.width && it.height == current.height }
        val pool = sameSize.ifEmpty { modes }
        if (pool.isEmpty()) return null
        // +0.5: a "60 Hz" mode is reported as 59.99 on several panels.
        val allowed = pool.filter { it.hz <= ceilingHz + 0.5f }
        return allowed.maxByOrNull { it.hz } ?: pool.minByOrNull { it.hz }
    }
}
