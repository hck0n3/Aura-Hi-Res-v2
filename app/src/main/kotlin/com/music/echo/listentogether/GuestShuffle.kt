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
