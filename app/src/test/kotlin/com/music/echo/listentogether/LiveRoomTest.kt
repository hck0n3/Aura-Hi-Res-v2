package iad1tya.echo.music.listentogether

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Escuchar juntos against the REAL server, with no phones.
 *
 * Ported from SimpMusic's `LiveServerHandshakeTest` / `LiveRoomSyncTest` (GPL-3.0) and adapted to
 * this app's API. Owner request 2026-09-15, after he pointed out — fairly — that handing him a
 * testing checklist is not the same as testing: everything else in this package is a conformance
 * test against bytes we shaped ourselves, which cannot falsify the assumption the whole port rests
 * on. Only a live server can say whether the protocol layer really works.
 *
 * What these DO cover: the handshake, that the server can parse bytes this app encoded, the clock
 * calibration, and a full two-client room — create, request, approve, change track, queue, play —
 * with the guest's state asserted at every step. If the server, the codec or the session machine
 * disagree anywhere, one of these fails.
 *
 * What they CANNOT cover, and nobody should read them as covering: the bridge between a room and
 * this app's ExoPlayer. Audio coming out of a speaker still needs two real phones.
 *
 * ## They do not run in the normal suite, on purpose
 * They need the network and they create rooms on someone else's server, so a green CI must not
 * depend on either. [assumeTrue] SKIPS them (JUnit "assumption failed", not a failure) unless
 * `LT_LIVE_TEST=1` is set — which is what the `Listen Together live test` workflow does, and what
 * anyone can do locally:
 *
 * ```
 * LT_LIVE_TEST=1 ./gradlew testUniversalFossDebugUnitTest --tests '*LiveRoomTest*' -i
 * ```
 */
class LiveRoomTest {
    /** Generous: this is a real network hop, not a latency assertion. */
    private val maxTravelMs = 5_000L

    private fun requireLiveTestsEnabled() =
        assumeTrue(
            "Skipped: set LT_LIVE_TEST=1 to run the live Listen Together tests",
            System.getenv("LT_LIVE_TEST") == "1",
        )

    /**
     * A JVM monotonic clock in place of the production default.
     *
     * [ListenTogetherClient] defaults to `SystemClock.elapsedRealtime`, which does not exist off a
     * device — passing one here is what keeps these plain JVM tests, with no Robolectric.
     */
    private fun client(version: String) =
        ListenTogetherClient(
            clientVersion = version,
            userAgent = "AuraHiRes/test (iad1tya.echo.music.test; JVM)",
            elapsedRealtime = { System.nanoTime() / 1_000_000 },
        )

    /** No token store: persistence belongs to the app, and these tests must not touch DataStore. */
    private fun session(version: String) = ListenTogetherSession(client(version), tokenStore = null)

    private suspend fun <T> eventually(
        label: String,
        timeoutMs: Long = 25_000,
        block: () -> T?,
    ): T? =
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                block()?.let { return@withTimeoutOrNull it }
                delay(150)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }.also { if (it == null) println("  ✗ timed out waiting for $label") }

    @Test
    fun theServerAnswersTheHandshakeAndCanParseOurBytes() =
        runBlocking {
            requireLiveTestsEnabled()
            val client = client("aura-live-test")
            val received = Channel<ListenTogetherEvent>(Channel.UNLIMITED)
            val collector = launch { client.events.collect { received.send(it) } }

            // Events already pulled off the channel while waiting for something else. Without this
            // the helper eats them: ClockReady legitimately arrives before room_created, and a
            // later wait for it would then block forever on an event that already happened.
            val seen = mutableListOf<ListenTogetherEvent>()

            suspend fun await(
                label: String,
                predicate: (ListenTogetherEvent) -> Boolean,
            ): ListenTogetherEvent? {
                seen.firstOrNull(predicate)?.let { return it }
                return withTimeoutOrNull(20_000) {
                    for (event in received) {
                        seen += event
                        if (predicate(event)) return@withTimeoutOrNull event
                    }
                    null
                }.also { if (it == null) println("  ✗ timed out waiting for $label") }
            }

            try {
                // The collector must be subscribed before the socket opens; events are not replayed.
                delay(200)
                println("→ connecting to ${ListenTogetherClient.DEFAULT_SERVER_URL}")
                client.connect()

                // 1. Both handshake type strings are the ones the server answers to.
                val connected = await("server_capabilities") { it is ListenTogetherEvent.Connected }
                assertNotNull("no handshake — the server never sent server_capabilities", connected)
                connected as ListenTogetherEvent.Connected
                println("✓ handshake ok — server version '${connected.serverVersion}'")

                // 2. The server can PARSE bytes we encoded, i.e. the protobuf equivalence holds.
                assertTrue(
                    "create_room could not be sent",
                    client.send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username = "AuraTest")),
                )
                val created =
                    await("room_created") {
                        it is ListenTogetherEvent.Message && it.type == MessageTypes.ROOM_CREATED
                    }
                assertNotNull("server did not answer room_created — it could not read our bytes", created)
                val room = (created as ListenTogetherEvent.Message).payload as? RoomCreatedPayload
                assertNotNull("room_created arrived but did not decode", room)
                room!!
                assertTrue("empty room code", room.roomCode.isNotEmpty())
                assertTrue("empty session token", room.sessionToken.isNotEmpty())
                println("✓ room created — code '${room.roomCode}'")

                // 3. Ping/pong round trips and ServerClock calibrates from them.
                val clock = await("ClockReady") { it is ListenTogetherEvent.ClockReady }
                assertNotNull("no pong ever calibrated the clock", clock)
                println("✓ server clock calibrated — serverNow=${client.serverNow()}")

                client.send(MessageTypes.LEAVE_ROOM, LeaveRoomPayload())
                delay(300)
            } finally {
                collector.cancel()
                client.release()
            }
        }

    @Test
    fun aGuestFollowsTheHostThroughARealRoom() =
        runBlocking {
            requireLiveTestsEnabled()
            val host = session("aura-host-test")
            val guest = session("aura-guest-test")

            try {
                host.connect()
                guest.connect()
                assertNotNull(
                    "host never connected",
                    eventually("host connected") { host.state.value.isConnected.takeIf { it } },
                )
                assertNotNull(
                    "guest never connected",
                    eventually("guest connected") { guest.state.value.isConnected.takeIf { it } },
                )
                println("✓ both clients connected")

                host.createRoom("HostUser")
                val code = eventually("room_created") { host.state.value.roomCode }
                assertNotNull("host never got a room", code)
                assertTrue("creator should be host", host.state.value.isHost)
                println("✓ room $code created")

                guest.joinRoom(code!!, "GuestUser")
                val request = eventually("join_request") { host.state.value.joinRequests.firstOrNull() }
                assertNotNull("host never saw the join request", request)
                assertEquals("GuestUser", request!!.username)
                println("✓ host received the join request")

                host.approveJoin(request.userId)
                assertNotNull(
                    "guest never got in",
                    eventually("join_approved") { guest.state.value.roomCode },
                )
                assertTrue("guest must not be host", !guest.state.value.isHost)
                println("✓ guest is in the room")

                // The whole point: a transport command issued by the host reaches the guest.
                val roomQueue =
                    listOf(
                        TrackInfo(id = "dQw4w9WgXcQ", title = "Test Track", artist = "Tester", duration = 180_000L),
                        TrackInfo(id = "9bZkp7q19f0", title = "Second", artist = "Tester", duration = 120_000L),
                        TrackInfo(id = "kJQP7kiw5Fk", title = "Third", artist = "Tester", duration = 150_000L),
                    )
                host.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = "dQw4w9WgXcQ",
                    position = 0L,
                    trackInfo = roomQueue.first(),
                    queue = roomQueue,
                    queueTitle = "Test Queue",
                )
                val track = eventually("track on guest") {
                    guest.state.value.currentTrack?.takeIf { it.id.isNotBlank() }
                }
                assertNotNull("the guest never received the host's track", track)
                assertEquals("dQw4w9WgXcQ", track!!.id)
                println("✓ guest received the track")

                // The queue must ride along with the track, not arrive separately — a guest with
                // only the current track plays its own next song and the room splits one track
                // later. The server keeps the queue as UPCOMING tracks only, so three sent comes
                // back as two.
                val gotQueue = eventually("queue on guest") { guest.state.value.queue.takeIf { it.size >= 2 } }
                assertNotNull("the guest never received the host's queue", gotQueue)
                assertEquals(listOf("9bZkp7q19f0", "kJQP7kiw5Fk"), gotQueue!!.map { it.id })
                println("✓ guest received the whole queue")

                host.sendPlaybackAction(PlaybackActions.PLAY, "dQw4w9WgXcQ", 12_345L, null)
                val playing = eventually("play on guest") { guest.state.value.takeIf { it.isPlaying } }
                assertNotNull("the guest never saw PLAY", playing)
                // NOT assertEquals: the server advances the position by the time the command spent
                // in flight, which is the synchronisation working rather than a mismatch.
                val drift = playing!!.position - 12_345L
                assertTrue(
                    "position should arrive advanced by travel time, not $drift ms off",
                    drift in 0..maxTravelMs,
                )
                println("✓ guest received PLAY at ${playing.position} (advanced ${drift}ms in flight)")

                val members = eventually("members") { guest.state.value.members.takeIf { it.size >= 2 } }
                assertNotNull("guest never saw a full member list", members)
                println("✓ guest sees ${members!!.size} members")

                host.leaveRoom()
                guest.leaveRoom()
                delay(300)
            } finally {
                host.release()
                guest.release()
            }
        }
}
