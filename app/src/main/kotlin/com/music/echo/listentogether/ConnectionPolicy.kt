package iad1tya.echo.music.listentogether

/** Pure decisions behind [ListenTogetherClient]'s connection upkeep, kept apart so they run as plain JVM tests. */
internal object ConnectionPolicy {
    /**
     * Row 744 (P1-9): steady-cadence pings in a row after which NOTHING at all has come back (no pong, no other frame) before the
     * socket counts as half-open and is reconnected. Two, so a dead socket is found within two to three ping intervals (30-45 s),
     * before the server's own 60 s read deadline would drop us from the room; one lost pong alone is not enough.
     */
    const val MAX_UNANSWERED_PINGS = 2

    /** The unanswered-ping count after one more ping interval; any inbound frame since that ping proves the socket alive. */
    fun unansweredAfter(previous: Int, heardSincePing: Boolean): Int = if (heardSincePing) 0 else previous + 1

    fun socketLooksDead(unansweredPings: Int): Boolean = unansweredPings >= MAX_UNANSWERED_PINGS
}
