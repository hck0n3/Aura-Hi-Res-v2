package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Plan
import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Registry row 536 / 539 — TWO devices running the real [LibrarySync] against a stand-in for the Worker that follows the Worker's rules
 * (`docs/worker/sync/src/index.js`: last writer wins, one log entry per item, «since N» pages, a signal to the others). What no unit
 * test of one piece can show: that the pieces, together, make a like, a playlist edit or a dislike made on one device show up on the other,
 * live, after being offline, without echoing back and without a wrong clock losing a deliberate change.
 */
class LibrarySyncSimulationTest {
    private lateinit var scope: CoroutineScope
    private lateinit var room: FakeRoom

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + Job())
        room = FakeRoom(scope)
        DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Off
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ── the world ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** The Worker's library part, in memory. */
    class FakeRoom(private val scope: CoroutineScope) {
        private class Entry(val ts: Long, val v: String, val seq: Long)

        private val kv = HashMap<String, Entry>()
        private val log = TreeMap<Long, Triple<String, Long, String>>()
        private var seq = 0L
        private val inboxes = LinkedHashMap<String, Channel<JsonObject>>()
        @Volatile var pushes = 0
        @Volatile var pulls = 0

        @Synchronized
        fun join(id: String, inbox: Channel<JsonObject>) {
            inboxes[id] = inbox
        }

        @Synchronized
        fun leave(id: String) {
            inboxes.remove(id)
        }

        val head: Long @Synchronized get() = seq

        @Synchronized
        fun receive(from: String, msg: JsonObject) {
            when ((msg["t"] as JsonPrimitive).content) {
                "lib_push" -> {
                    pushes++
                    var accepted = 0
                    for (op in msg["ops"] as JsonArray) {
                        val a = op as JsonArray
                        val key = (a[0] as JsonPrimitive).content
                        val ts = (a[1] as JsonPrimitive).longOrNull ?: continue
                        val v = (a[2] as JsonPrimitive).content
                        val cur = kv[key]
                        if (cur != null && !(ts > cur.ts || (ts == cur.ts && v > cur.v))) continue
                        seq++
                        if (cur != null) log.remove(cur.seq)
                        kv[key] = Entry(ts, v, seq)
                        log[seq] = Triple(key, ts, v)
                        accepted++
                    }
                    deliver(from, buildJsonObject { put("t", "lib_ack"); put("seq", seq); put("accepted", accepted) })
                    if (accepted > 0) inboxes.keys.filter { it != from }.forEach { deliver(it, buildJsonObject { put("t", "lib_ops"); put("seq", seq) }) }
                }
                "lib_pull" -> {
                    pulls++
                    var since = (msg["since"] as JsonPrimitive).longOrNull ?: 0L
                    val reset = since > seq
                    if (reset) since = 0
                    val page = log.tailMap(since + 1).entries.take(400)
                    val items = buildJsonArray {
                        page.forEach { (_, e) ->
                            add(buildJsonArray { add(JsonPrimitive(e.first)); add(JsonPrimitive(e.second)); add(JsonPrimitive(e.third)) })
                        }
                    }
                    val last = page.lastOrNull()?.key ?: since
                    deliver(from, buildJsonObject {
                        put("t", "lib_state"); put("items", items); put("seq", last); put("head", seq)
                        put("more", page.size == 400 && last < seq); put("reset", reset)
                    })
                }
            }
        }

        private fun deliver(to: String, msg: JsonObject) {
            val inbox = inboxes[to] ?: return
            scope.launch { inbox.send(msg) }
        }

        fun value(key: String): String? = kv[key]?.v
    }

    /** What a device has: a library kept in memory. */
    class FakeBackend : LibraryBackend {
        val songs = HashMap<String, LibrarySyncCore.SongMeta>()
        val likes = HashSet<String>()
        val dislikes = HashSet<Pair<String, String>>()
        val playlists = LinkedHashMap<String, String>()
        val members = LinkedHashSet<Pair<String, String>>()
        val albums = HashMap<String, LibrarySyncCore.AlbumMeta>()
        val savedAlbums = HashSet<String>()
        var applied = 0

        fun saveAlbum(id: String) = synchronized(lock) {
            albums.getOrPut(id) { LibrarySyncCore.AlbumMeta("Álbum $id", "http://c/$id", "OLAK$id", 2020, 10, 2400) }
            savedAlbums += id
        }

        fun addSong(id: String, title: String = "Título $id") = synchronized(lock) {
            songs[id] = LibrarySyncCore.SongMeta(title, "Artista", "http://c/$id")
        }

        fun like(id: String) = synchronized(lock) {
            if (id !in songs) addSong(id)
            likes += id
        }

        private val lock = Any()

        override suspend fun readLocal(songMetaKnown: (String) -> Boolean): Map<String, String> = synchronized(lock) {
            val facts = LinkedHashMap<String, String>()
            val referenced = LinkedHashSet<String>()
            likes.forEach { facts[LibrarySyncCore.likeKey(it)] = "1"; referenced += it }
            playlists.forEach { (k, n) -> facts[LibrarySyncCore.playlistKey(k)] = LibrarySyncCore.encodePlaylistName(n) }
            members.forEach { (p, s) -> if (p in playlists) { facts[LibrarySyncCore.memberKey(p, s)] = "1"; referenced += s } }
            dislikes.forEach { (set, id) -> facts[LibrarySyncCore.dislikeKey(set, id)] = "1" }
            savedAlbums.forEach { id ->
                facts[LibrarySyncCore.albumKey(id)] = "1"
                if (!songMetaKnown(LibrarySyncCore.albumMetaKey(id))) albums[id]?.let { facts[LibrarySyncCore.albumMetaKey(id)] = LibrarySyncCore.encodeAlbum(it) }
            }
            referenced.filter { !songMetaKnown(LibrarySyncCore.songKey(it)) }.forEach { id ->
                songs[id]?.let { facts[LibrarySyncCore.songKey(id)] = LibrarySyncCore.encodeSong(it) }
            }
            facts
        }

        override suspend fun apply(plan: Plan): ApplyResult = synchronized(lock) {
            var unresolved = 0
            for ((id, meta) in plan.songs) if (id !in songs) songs[id] = meta
            for ((k, n) in plan.playlistUpserts) playlists[k] = n
            for ((p, s) in plan.memberAdds) if (p in playlists && s in songs) members += p to s else unresolved++
            for ((id, on) in plan.likes) if (on) { if (id in songs) likes += id else unresolved++ } else likes -= id
            for ((set, id, on) in plan.dislikes) if (on) dislikes += set to id else dislikes -= set to id
            for ((id, meta) in plan.albums) albums.putIfAbsent(id, meta)
            for ((id, on) in plan.albumSaves) if (on) { if (id in albums) savedAlbums += id else unresolved++ } else savedAlbums -= id
            for ((p, s) in plan.memberRemovals) members -= p to s
            for (p in plan.playlistRemovals) { playlists -= p; members.removeAll { it.first == p } }
            applied++
            ApplyResult(applied, unresolved)
        }
    }

    class FakeLedger : LedgerPersistence {
        var stored = HashMap<String, Reg>()
        override fun load(): HashMap<String, Reg> = HashMap(stored)
        override fun append(regs: Collection<Reg>) { regs.forEach { stored[it.key] = it } }
        override fun compactIfBloated(map: Map<String, Reg>) = false
        override fun rewrite(map: Map<String, Reg>) { stored = HashMap(map) }
    }

    inner class Device(val id: String, clockSkewMs: Long = 0L, val enabled: () -> Boolean = { true }) {
        val backend = FakeBackend()
        val ledger = FakeLedger()
        private var since = 0L
        private val inbox = Channel<JsonObject>(Channel.UNLIMITED)
        @Volatile var online = false
        private var consumer: Job? = null
        val sync = LibrarySync(
            adapter = backend,
            store = ledger,
            sinceStore = object : SinceStore {
                override fun get() = since
                override fun put(value: Long) { since = value }
            },
            scope = scope,
            sendMessage = { msg -> if (online) { room.receive(id, msg); true } else false },
            isEnabled = enabled,
            clock = { System.currentTimeMillis() + clockSkewMs },
            timing = LibrarySync.Timing(fastDelayMs = 20, slowDelayMs = 20, minIntervalMs = 20, pullTimeoutMs = 2_000),
        )

        fun connect() {
            online = true
            room.join(id, inbox)
            consumer = scope.launch { for (m in inbox) sync.handle(m) }
            sync.onConnected(serverLib = 2)
        }

        fun disconnect() {
            online = false
            room.leave(id)
            consumer?.cancel()
            sync.onDisconnected()
        }

        /** The user acts on this device: the hook the app calls (`libraryChanged`). */
        fun changed() = sync.kick(fast = true)
    }

    private fun eventually(what: String, ms: Long = 4_000, condition: () -> Boolean) = runBlocking {
        try {
            withTimeout(ms) { while (!condition()) delay(10) }
        } catch (e: Exception) {
            throw AssertionError("never happened: $what")
        }
    }

    private fun settle() = runBlocking { delay(300) }

    // ── the scenarios ───────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a new device receives the whole library of the other, songs included`() {
        val phone = Device("phone")
        phone.backend.like("s1"); phone.backend.like("s2")
        phone.backend.playlists["LPabcdefgh"] = "Gym"
        phone.backend.addSong("s3"); phone.backend.members += "LPabcdefgh" to "s3"
        phone.backend.dislikes += "song" to "bad1"
        phone.connect()
        settle()

        val tablet = Device("tablet")
        tablet.connect()
        eventually("tablet has the likes") { tablet.backend.likes == setOf("s1", "s2") }
        eventually("tablet has the playlist and its song") { tablet.backend.playlists["LPabcdefgh"] == "Gym" && ("LPabcdefgh" to "s3") in tablet.backend.members }
        assertTrue("the song's title came with it", tablet.backend.songs["s1"]?.title == "Título s1")
        assertTrue(("song" to "bad1") in tablet.backend.dislikes)
    }

    @Test
    fun `a big library pushed in many batches does not make the other device pull it over and over`() {
        val phone = Device("phone")
        for (i in 1..7_000) phone.backend.like("big$i")
        val tablet = Device("tablet")
        tablet.connect() // already in the room while the phone sends everything: it gets one «lib_ops» per batch
        phone.connect()
        eventually("the tablet has every like", ms = 20_000) { tablet.backend.likes.size == 7_000 }
        settle()
        val pages = 14_000 / 400 + 1
        assertTrue("the tablet asked ${room.pulls} times for ~$pages pages of data", room.pulls <= pages * 4)
        // And once caught up it can send: a like made on the tablet reaches the phone.
        tablet.backend.addSong("fresh"); tablet.backend.like("fresh"); tablet.changed()
        eventually("the phone gets the like the tablet made after catching up", ms = 10_000) { "fresh" in phone.backend.likes }
    }

    @Test
    fun `a saved album travels as the album only, never as likes of its songs, and an unsave travels too`() {
        val phone = Device("phone")
        phone.backend.saveAlbum("MPREb_one")
        phone.backend.like("solo") // a song liked on its own
        phone.connect()
        settle()
        val tablet = Device("tablet")
        tablet.connect()
        eventually("the tablet has the saved album with its details") {
            "MPREb_one" in tablet.backend.savedAlbums && tablet.backend.albums["MPREb_one"]?.title == "Álbum MPREb_one"
        }
        assertEquals("the album's songs are NOT liked on the other device", setOf("solo"), tablet.backend.likes)
        phone.backend.savedAlbums -= "MPREb_one"; phone.changed()
        eventually("the unsave reaches the tablet") { "MPREb_one" !in tablet.backend.savedAlbums }
        tablet.backend.saveAlbum("MPREb_two"); tablet.changed()
        eventually("an album saved on the tablet reaches the phone") { "MPREb_two" in phone.backend.savedAlbums }
    }

    @Test
    fun `a like, an unlike and a dislike made on one device show up on the other live`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.connect(); tablet.connect()
        settle()

        phone.backend.like("liveSong"); phone.changed()
        eventually("the like reaches the tablet") { "liveSong" in tablet.backend.likes }

        phone.backend.dislikes += "artist" to "UC1"; phone.changed()
        eventually("the dislike reaches the tablet") { ("artist" to "UC1") in tablet.backend.dislikes }

        phone.backend.likes -= "liveSong"; phone.changed()
        eventually("the unlike reaches the tablet") { "liveSong" !in tablet.backend.likes }
    }

    @Test
    fun `playlist edits travel - create, add, remove, rename, delete`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.connect(); tablet.connect()
        settle()

        phone.backend.playlists["LPplaylist1"] = "Mi lista"; phone.backend.addSong("a"); phone.backend.addSong("b")
        phone.backend.members += "LPplaylist1" to "a"; phone.backend.members += "LPplaylist1" to "b"
        phone.changed()
        eventually("create + add") { tablet.backend.playlists["LPplaylist1"] == "Mi lista" && tablet.backend.members.size == 2 }

        phone.backend.members -= "LPplaylist1" to "a"; phone.changed()
        eventually("remove a song") { tablet.backend.members == setOf("LPplaylist1" to "b") }

        phone.backend.playlists["LPplaylist1"] = "Renombrada"; phone.changed()
        eventually("rename") { tablet.backend.playlists["LPplaylist1"] == "Renombrada" }

        phone.backend.playlists.remove("LPplaylist1"); phone.backend.members.clear(); phone.changed()
        eventually("delete") { tablet.backend.playlists.isEmpty() && tablet.backend.members.isEmpty() }
    }

    @Test
    fun `changes made while a device was offline arrive when it connects`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.connect(); tablet.connect()
        settle()
        tablet.disconnect()

        phone.backend.like("whileAway"); phone.backend.playlists["LPawaytest1"] = "Mientras no estabas"
        phone.changed()
        settle()
        assertTrue("the tablet was away, it knows nothing", "whileAway" !in tablet.backend.likes)

        tablet.connect()
        eventually("it catches up on connect") { "whileAway" in tablet.backend.likes && tablet.backend.playlists["LPawaytest1"] == "Mientras no estabas" }
    }

    @Test
    fun `a change made while OFFLINE is sent when the device reconnects, not lost`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.connect(); tablet.connect()
        settle()
        phone.disconnect()
        phone.backend.like("offlineLike")
        phone.changed() // nothing can be sent: the hook is a no-op while disconnected
        settle()
        assertTrue("offlineLike" !in tablet.backend.likes)

        phone.connect()
        eventually("the offline like goes out on reconnect") { "offlineLike" in tablet.backend.likes }
    }

    @Test
    fun `nothing echoes - once both agree, no more messages are pushed`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.backend.like("s1")
        phone.connect(); tablet.connect()
        eventually("agree") { "s1" in tablet.backend.likes }
        settle()
        val pushesAfterSync = room.pushes
        val head = room.head
        phone.changed(); tablet.changed()
        settle(); settle()
        assertEquals("no new registers from an unchanged library", head, room.head)
        assertEquals(pushesAfterSync, room.pushes)
    }

    @Test
    fun `a device whose clock is an hour behind still wins when it unlikes what the other one liked later`() {
        val phone = Device("phone", clockSkewMs = 0)
        val tablet = Device("tablet", clockSkewMs = -3_600_000L)
        phone.connect(); tablet.connect()
        settle()
        phone.backend.like("song"); phone.changed()
        eventually("tablet has the like") { "song" in tablet.backend.likes }

        tablet.backend.likes -= "song"; tablet.changed() // its clock says an hour AGO — the unlike must not be discarded as old
        eventually("the unlike wins on the phone") { "song" !in phone.backend.likes }
    }

    @Test
    fun `a mass deletion from the other device is held, additions still arrive`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        repeat(40) { phone.backend.like("keep$it") }
        phone.connect(); tablet.connect()
        eventually("tablet has all 40") { tablet.backend.likes.size == 40 }
        settle()

        // the phone is wiped (a bug, an emptied account): 40 removals plus one genuine new like
        phone.backend.likes.clear(); phone.backend.like("genuinelyNew"); phone.changed()
        eventually("the new like arrives") { "genuinelyNew" in tablet.backend.likes }
        settle()
        assertTrue("the removals were held: the tablet still has its likes", tablet.backend.likes.size >= 40)
    }

    @Test
    fun `an old server is reported, not waited for in silence`() {
        val phone = Device("phone")
        phone.online = true
        room.join("phone", Channel(Channel.UNLIMITED))
        phone.sync.onConnected(serverLib = 0)
        assertEquals(DeviceSyncHub.LibraryStatus.ServerOutdated, DeviceSyncHub.libraryStatus.value)
        assertFalse(DeviceSyncHub.libraryOwnedByDevices)
        assertEquals("no library message was sent to a server that cannot answer", 0, room.pushes)
    }

    @Test
    fun `switched off, the library sync sends and applies nothing`() {
        var on = false
        val phone = Device("phone", enabled = { on }); val tablet = Device("tablet")
        phone.backend.like("s1")
        phone.connect(); tablet.connect()
        settle()
        assertTrue("s1" !in tablet.backend.likes)
        assertEquals(0L, room.head)
        on = true
        phone.sync.resync()
        eventually("switched on, it syncs") { "s1" in tablet.backend.likes }
    }

    @Test
    fun `the status says what happened`() {
        val phone = Device("phone")
        phone.backend.like("s1")
        phone.connect()
        eventually("synced status") { DeviceSyncHub.libraryStatus.value is DeviceSyncHub.LibraryStatus.Synced }
        val s = DeviceSyncHub.libraryStatus.value as DeviceSyncHub.LibraryStatus.Synced
        assertTrue(s.known >= 1)
        eventually("nothing left to send") { (DeviceSyncHub.libraryStatus.value as? DeviceSyncHub.LibraryStatus.Synced)?.pending == 0 }
    }
}
