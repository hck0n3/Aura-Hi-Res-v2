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

    /**
     * Row 744 (P2-9): how long a client that holds a room's session token keeps trying to get back to it after the socket drops.
     * Five attempts (1+2+4+8+16 s) were spent in about 31 s, so a phone in Doze with a long-paused room lost the room — and its
     * token — on the first network blackout. Ten minutes is twice the time Aura's rooms keep a room nobody is connected to
     * (`EMPTY_ROOM_MS`, 5 min, docs/worker/rooms); the delay between attempts still doubles up to one minute, so it is about a
     * dozen attempts in all.
     */
    const val ROOM_RESUME_WINDOW_MS = 10 * 60_000L

    /**
     * Whether a dropped connection stops retrying. Inside a room ([inRoom], a session token is held) only the time since the drop
     * counts, so the attempts are not used up while the network is simply away; outside a room the attempt budget stays.
     */
    fun shouldGiveUp(
        inRoom: Boolean,
        attempts: Int,
        maxAttempts: Int,
        droppedAtMs: Long,
        nowMs: Long,
    ): Boolean = if (inRoom) nowMs - droppedAtMs >= ROOM_RESUME_WINDOW_MS else attempts >= maxAttempts

    /**
     * Row 744: the HTTP status of a refused WebSocket upgrade, read from OkHttp's `Expected HTTP 101 response but was '404 Not Found'`
     * anywhere in the cause chain; null for any other failure (no network, timeout, TLS), which stays a reason to retry.
     */
    fun refusedUpgradeStatus(error: Throwable): Int? =
        generateSequence(error) { t -> t.cause?.takeIf { it !== t } }
            .take(8)
            .firstNotNullOfOrNull { t -> REFUSED_UPGRADE.find(t.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }

    /** docs/worker/rooms `/rooms/join`: 404 for a room that does not exist, 400 for a malformed code. Neither changes on a retry. */
    fun roomIsGone(status: Int): Boolean = status == 404 || status == 400

    private val REFUSED_UPGRADE = Regex("""Expected HTTP 101 response but was '(\d{3})""")
}
