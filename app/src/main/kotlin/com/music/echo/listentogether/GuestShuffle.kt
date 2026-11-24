package iad1tya.echo.music.listentogether

/**
 * Registry rows 848-849 (owner decision B2, 2026-11-24): inside a Listen Together room the order is the host's, so a guest
 * cannot shuffle. A guest with shuffle on walked the room's queue in ITS own random order at the end of each song and
 * preloaded that one (row 796, plausible (a)). Pure.
 */
object GuestShuffle {
    /** Whether this device may turn shuffle on: outside a room, or as its host. */
    fun mayShuffle(inRoom: Boolean, isHost: Boolean): Boolean = !inRoom || isHost
}

/**
 * Row 849: the user's own shuffle while this device is a guest. Becoming a guest remembers it and switches it off; no longer
 * being one (leaving, being kicked, the room ending, or becoming the host) gives it back. Synchronized: the role and a new
 * player arrive on different collectors.
 */
internal class GuestShuffleHold {
    private var saved: Boolean? = null

    /** The shuffle value to apply now, or null to leave the player as it is. */
    @Synchronized
    fun onRole(guest: Boolean, shuffleOn: Boolean): Boolean? {
        if (guest) {
            if (saved == null) saved = shuffleOn
            return if (shuffleOn) false else null
        }
        val restore = saved ?: return null
        saved = null
        return if (restore != shuffleOn) restore else null
    }
}
