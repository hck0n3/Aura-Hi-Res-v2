package iad1tya.echo.music.utils

import androidx.datastore.preferences.core.Preferences
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Owner 2026-11-23: «un ID de registro distinto por cada acción o botón» (registry row 730).
 *
 * Every tap writes ONE INFO line to the shared `app.log`: `ACT a=<n> <code>`, where `<code>` is the button's stable [ActionId]
 * and `<n>` grows by one per action in this process. For [JOIN_WINDOW_MS] after a tap, [AppLogger] ends every other line it
 * writes (the service's START_CAUSE / ERR_BR / resolve lines included) with ` a=<n>`, so the owner can join a tap with what it
 * caused. Settings toggles write the preference KEY name and the new boolean / enum value ([setting]).
 *
 * Rule 4 by construction: the only inputs are an [ActionId], a [Preferences.Key] (a constant in code) and a Boolean or enum —
 * there is no parameter a title, an artist, a name or any typed text could reach. `ActionLogGuardTest` keeps it that way.
 * Rule 7: called from click handlers only, never from a frame or tick loop.
 */
object ActionLog {
    const val TAG = "ACTION"

    /** How long after a tap the following lines carry its number. Chosen for this change (the task asked for ~3 s). */
    const val JOIN_WINDOW_MS = 3_000L

    private class Last(val number: Long, val atMs: Long)

    private val counter = AtomicLong()

    @Volatile
    private var last: Last? = null

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** One button or action. */
    fun tap(id: ActionId) {
        val n = record(nowMs())
        Timber.tag(TAG).i("ACT a=%d %s", n, id.code)
    }

    /** A boolean setting changed by the user: its key NAME and the new value, never anything typed. */
    fun setting(key: Preferences.Key<*>, value: Boolean) {
        val n = record(nowMs())
        Timber.tag(TAG).i("ACT a=%d %s key=%s value=%b", n, ActionId.SET_TOGGLE.code, key.name, value)
    }

    /** A choice setting (an enum) changed by the user: its key NAME and the new constant's name. */
    fun setting(key: Preferences.Key<*>, value: Enum<*>) {
        val n = record(nowMs())
        Timber.tag(TAG).i("ACT a=%d %s key=%s value=%s", n, ActionId.SET_CHOICE.code, key.name, value.name)
    }

    /** ` a=<n>` while the last action is younger than [JOIN_WINDOW_MS], else empty. Read by [AppLogger] for every line. */
    fun correlationSuffix(): String = suffixAt(nowMs())

    internal fun record(atMs: Long): Long {
        val n = counter.incrementAndGet()
        last = Last(n, atMs)
        return n
    }

    internal fun suffixAt(nowMs: Long): String {
        val l = last ?: return ""
        return if (nowMs - l.atMs in 0..JOIN_WINDOW_MS) " a=${l.number}" else ""
    }
}
