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
            val unplaced = HashSet<String>()
            for ((id, meta) in plan.songs) if (id !in songs) songs[id] = meta
            for ((k, n) in plan.playlistUpserts) playlists[k] = n
            for ((p, s) in plan.memberAdds) if (p in playlists && s in songs) members += p to s else { unresolved++; unplaced += LibrarySyncCore.memberKey(p, s) }
            for ((id, on) in plan.likes) if (on) { if (id in songs) likes += id else { unresolved++; unplaced += LibrarySyncCore.likeKey(id) } } else likes -= id
            for ((set, id, on) in plan.dislikes) if (on) dislikes += set to id else dislikes -= set to id
            for ((id, meta) in plan.albums) albums.putIfAbsent(id, meta)
            for ((id, on) in plan.albumSaves) if (on) { if (id in albums) savedAlbums += id else { unresolved++; unplaced += LibrarySyncCore.albumKey(id) } } else savedAlbums -= id
            for ((p, s) in plan.memberRemovals) members -= p to s
            for (p in plan.playlistRemovals) { playlists -= p; members.removeAll { it.first == p } }
            applied++
            ApplyResult(applied, unresolved, unplaced)
        }
    }

    class FakeLedger : LedgerPersistence {
        var stored = HashMap<String, Reg>()
        override fun load(): HashMap<String, Reg> = HashMap(stored)
        override fun append(regs: Collection<Reg>) { regs.forEach { stored[it.key] = it } }
        override fun compactIfBloated(map: Map<String, Reg>) = false
        override fun rewrite(map: Map<String, Reg>) { stored = HashMap(map) }
    }

    inner class Device(
        val id: String,
        clockSkewMs: Long = 0L,
        val enabled: () -> Boolean = { true },
        private var fresh: Boolean = false,
        /** A restarted app: the same library, ledger and since as an earlier instance, with nothing of its memory. */
        val backend: FakeBackend = FakeBackend(),
        val ledger: FakeLedger = FakeLedger(),
        var since: Long = 0L,
        clockKnown: () -> Boolean = { true },
    ) {
        private val inbox = Channel<JsonObject>(Channel.UNLIMITED)
        @Volatile var online = false
        private var consumer: Job? = null
        val sync = LibrarySync(
            adapter = backend,
            store = ledger,
            sinceStore = object : SinceStore {
                override fun get() = since
                override fun put(value: Long) { since = value }
                override fun freshStart() = fresh
                override fun endFreshStart() { fresh = false }
            },
            scope = scope,
            sendMessage = { msg -> if (online) { room.receive(id, msg); true } else false },
            isEnabled = enabled,
            clock = { System.currentTimeMillis() + clockSkewMs },
            timing = LibrarySync.Timing(fastDelayMs = 20, slowDelayMs = 20, minIntervalMs = 20, pullTimeoutMs = 2_000),
            clockKnown = clockKnown,
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

    // Owner 2026-11-22: a change made while a device was away used to get its time when it was SENT, i.e. after the room's
    // state had already been applied over it. These three pin «newest wins by when the change was MADE».

    @Test
    fun `a like made offline is not undone by an older unlike the room got while the device was away`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.backend.like("known")
        phone.connect(); tablet.connect()
        eventually("both caught up") { "known" in tablet.backend.likes }
        settle()
        tablet.disconnect()
        // Monday: the phone likes Y and then changes its mind.
        phone.backend.like("Y"); phone.changed()
        eventually("the like is in the room") { room.value("lk:Y") == "1" }
        phone.backend.likes -= "Y"; phone.changed()
        eventually("the unlike is in the room") { room.value("lk:Y") == "0" }
        settle()
        // Tuesday, offline: the tablet likes Y.
        tablet.backend.like("Y"); tablet.changed()
        settle()
        tablet.connect()
        eventually("the tablet's newer like reaches the room") { room.value("lk:Y") == "1" }
        eventually("and the phone") { "Y" in phone.backend.likes }
        settle()
        assertTrue("the tablet kept its like", "Y" in tablet.backend.likes)
    }

    @Test
    fun `an unlike made offline is not undone by an older like the room got while the device was away`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.backend.like("Y")
        phone.connect(); tablet.connect()
        eventually("both have Y") { "Y" in tablet.backend.likes }
        settle()
        tablet.disconnect()
        // Monday: the phone unlikes Y and likes it again.
        phone.backend.likes -= "Y"; phone.changed()
        eventually("the unlike is in the room") { room.value("lk:Y") == "0" }
        phone.backend.like("Y"); phone.changed()
        eventually("the like is in the room again") { room.value("lk:Y") == "1" }
        settle()
        // Tuesday, offline: the tablet unlikes Y.
        tablet.backend.likes -= "Y"; tablet.changed()
        settle()
        tablet.connect()
        eventually("the tablet's newer unlike reaches the room") { room.value("lk:Y") == "0" }
        eventually("and the phone") { "Y" !in phone.backend.likes }
        settle()
        assertFalse("the tablet kept its unlike", "Y" in tablet.backend.likes)
    }

    @Test
    fun `a change the room got AFTER the offline one still wins`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.backend.like("Y")
        phone.connect(); tablet.connect()
        eventually("both have Y") { "Y" in tablet.backend.likes }
        settle()
        tablet.disconnect()
        // First the offline tablet unlikes Y, then the phone unlikes and likes it again: the phone's like is the newest.
        tablet.backend.likes -= "Y"; tablet.changed()
        settle()
        phone.backend.likes -= "Y"; phone.changed()
        eventually("the unlike is in the room") { room.value("lk:Y") == "0" }
        phone.backend.like("Y"); phone.changed()
        eventually("the like is in the room again") { room.value("lk:Y") == "1" }
        settle()
        tablet.connect()
        eventually("the newest change, the phone's like, reaches the tablet") { "Y" in tablet.backend.likes }
        settle()
        assertEquals("1", room.value("lk:Y"))
        assertTrue("Y" in phone.backend.likes)
    }

    @Test
    fun `a device that never caught up with the room does not turn the library it brought into new changes while offline`() {
        val tablet = Device("tablet")
        repeat(3) { tablet.backend.like("w$it") }
        tablet.connect()
        eventually("the room has the likes") { (0 until 3).all { room.value("lk:w$it") == "1" } }
        tablet.backend.likes.clear(); tablet.changed()
        eventually("the room has the removals") { (0 until 3).all { room.value("lk:w$it") == "0" } }

        // A phone new to this room still has them, and something kicks its sync before it ever connects.
        val phone = Device("phone")
        repeat(3) { phone.backend.like("w$it") }
        phone.changed()
        settle()
        assertTrue("nothing recorded before the first catch-up", phone.ledger.stored.isEmpty())
        phone.connect()
        eventually("the phone caught up") { (0 until 3).all { phone.ledger.stored["lk:w$it"]?.value == "0" } }
        settle()
        assertTrue("the room's removals stand", (0 until 3).all { room.value("lk:w$it") == "0" })
        assertTrue("nothing the user removed came back on the tablet", tablet.backend.likes.isEmpty())
    }

    // Review of the offline stamp: a change nobody watched being made must not be dated when it is FOUND, or an old change of a
    // stale device beats newer changes of the others. In these cases the room's newer change wins, as before the stamp existed.

    private fun twoDevicesWithPlaylist(tablet: Device): Device {
        val phone = Device("phone")
        phone.backend.playlists["LPgymgymgy"] = "Gym"
        phone.connect(); tablet.connect()
        eventually("the tablet has the playlist") { tablet.backend.playlists["LPgymgymgy"] == "Gym" }
        settle()
        return phone
    }

    @Test
    fun `a rename made while Biblioteca was off does not beat a newer rename once it is back on offline`() {
        var on = true
        val tablet = Device("tablet", enabled = { on })
        val phone = twoDevicesWithPlaylist(tablet)
        // Monday: Biblioteca off on the tablet, the playlist renamed there.
        on = false
        tablet.backend.playlists["LPgymgymgy"] = "Correr"; tablet.changed()
        settle()
        // Tuesday: renamed on the phone.
        phone.backend.playlists["LPgymgymgy"] = "Gym 2"; phone.changed()
        eventually("the phone's rename is in the room") { room.value("pl:LPgymgymgy") == LibrarySyncCore.encodePlaylistName("Gym 2") }
        // Friday: the tablet, offline, turns Biblioteca back on and something changes in its database.
        tablet.disconnect()
        on = true
        tablet.changed()
        settle()
        tablet.connect()
        eventually("the newer rename reaches the tablet") { tablet.backend.playlists["LPgymgymgy"] == "Gym 2" }
        settle()
        assertEquals(LibrarySyncCore.encodePlaylistName("Gym 2"), room.value("pl:LPgymgymgy"))
        assertEquals("Gym 2", phone.backend.playlists["LPgymgymgy"])
    }

    @Test
    fun `a change made while the app was not running does not beat a newer one after a restart offline`() {
        val tablet = Device("tablet")
        val phone = twoDevicesWithPlaylist(tablet)
        // Monday: the tablet's sync is gone (app or service not running) and something renames the playlist there.
        tablet.disconnect(); tablet.sync.stop()
        tablet.backend.playlists["LPgymgymgy"] = "Correr"
        settle()
        // Tuesday: renamed on the phone.
        phone.backend.playlists["LPgymgymgy"] = "Gym 2"; phone.changed()
        eventually("the phone's rename is in the room") { room.value("pl:LPgymgymgy") == LibrarySyncCore.encodePlaylistName("Gym 2") }
        // Friday: the app starts again, still offline, with the same library, ledger and since.
        val restarted = Device("tablet", backend = tablet.backend, ledger = tablet.ledger, since = tablet.since)
        restarted.changed()
        settle()
        assertTrue("nothing recorded by an instance that never caught up", restarted.ledger.stored["pl:LPgymgymgy"]?.pending != true)
        restarted.connect()
        eventually("the newer rename reaches the tablet") { restarted.backend.playlists["LPgymgymgy"] == "Gym 2" }
        settle()
        assertEquals(LibrarySyncCore.encodePlaylistName("Gym 2"), room.value("pl:LPgymgymgy"))
    }

    @Test
    fun `without the server's time in this process a fast clock never dates an offline change`() {
        // Two hours fast, and no hello_ok ever gave this process the server's offset.
        val tablet = Device("tablet", clockSkewMs = 2 * 3_600_000L, clockKnown = { false })
        val phone = twoDevicesWithPlaylist(tablet)
        tablet.disconnect()
        tablet.backend.playlists["LPgymgymgy"] = "Correr"; tablet.changed()
        settle()
        phone.backend.playlists["LPgymgymgy"] = "Gym 2"; phone.changed()
        eventually("the phone's rename is in the room") { room.value("pl:LPgymgymgy") == LibrarySyncCore.encodePlaylistName("Gym 2") }
        tablet.connect()
        eventually("the newer rename reaches the tablet") { tablet.backend.playlists["LPgymgymgy"] == "Gym 2" }
        settle()
        assertEquals(LibrarySyncCore.encodePlaylistName("Gym 2"), room.value("pl:LPgymgymgy"))
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
    fun `a device new to the room keeps what a braked mass removal took away, and never sends it back as new`() {
        val tablet = Device("tablet")
        repeat(40) { tablet.backend.like("w$it") }
        tablet.connect()
        eventually("the room has the 40 likes") { (0 until 40).all { room.value("lk:w$it") == "1" } }
        // The user clears all 40 at once on the tablet: more than the brake lets another device apply.
        tablet.backend.likes.clear(); tablet.changed()
        eventually("the room has the 40 removals") { (0 until 40).all { room.value("lk:w$it") == "0" } }

        // A phone joins this room with an empty ledger (a room switch, a new pairing) and still has those 40 in its library.
        val phone = Device("phone")
        repeat(40) { phone.backend.like("w$it") }
        phone.connect()
        eventually("the phone caught up") { phone.ledger.stored.keys.count { it.startsWith("lk:") } == 40 }
        phone.changed()
        settle(); settle()
        assertEquals("the phone's copies were not deleted on a hunch", 40, phone.backend.likes.size)
        assertTrue("and not sent back to the room as new likes", (0 until 40).all { room.value("lk:w$it") == "0" })
        assertTrue("so nothing the user removed came back on the tablet", tablet.backend.likes.isEmpty())
    }

    @Test
    fun `a stopped sync sends no pull on the socket, and a sync that is not connected applies no state`() {
        val phone = Device("phone")
        phone.connect()
        eventually("the phone caught up") { DeviceSyncHub.libraryStatus.value is DeviceSyncHub.LibraryStatus.Synced }
        settle()
        // The room changes (a room switch stops this instance): a late signal must not make it pull on the new socket.
        phone.sync.stop()
        val pulls = room.pulls
        runBlocking { phone.sync.handle(buildJsonObject { put("t", "lib_ops"); put("seq", 1) }) }
        settle()
        assertEquals("the stopped instance sent no lib_pull", pulls, room.pulls)

        // A state that arrives before this instance is connected (a stale answer from the previous room) is not applied.
        val tablet = Device("tablet")
        runBlocking {
            tablet.sync.handle(buildJsonObject {
                put("t", "lib_state"); put("seq", 7); put("more", false)
                put("items", buildJsonArray {
                    add(buildJsonArray { add(JsonPrimitive("sg:stale")); add(JsonPrimitive(1L)); add(JsonPrimitive(LibrarySyncCore.encodeSong(LibrarySyncCore.SongMeta("t", "a", "c")))) })
                    add(buildJsonArray { add(JsonPrimitive("lk:stale")); add(JsonPrimitive(1L)); add(JsonPrimitive("1")) })
                })
            })
        }
        assertTrue("nothing was applied", tablet.backend.likes.isEmpty() && tablet.backend.songs.isEmpty())
        assertTrue("and nothing was written to its ledger", tablet.ledger.stored.isEmpty())
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

    @Test
    fun `a register of a family this build does not understand is never removed by it`() {
        val phone = Device("phone")
        phone.backend.like("s1")
        phone.connect()
        eventually("the phone's like is in the room") { room.value("lk:s1") == "1" }
        // A newer app version wrote a kind of register this build cannot read.
        room.receive("newer", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { add(buildJsonArray { add(JsonPrimitive("zz:future")); add(JsonPrimitive(System.currentTimeMillis())); add(JsonPrimitive("1")) }) })
        })
        val tablet = Device("tablet")
        tablet.connect()
        eventually("the tablet caught up") { "s1" in tablet.backend.likes }
        tablet.changed(); phone.changed()
        settle(); settle()
        assertEquals("the unknown register is still on in the room", "1", room.value("zz:future"))
    }

    @Test
    fun `a playlist song that arrives a page before its playlist is placed later, never sent back as a removal`() {
        val phone = Device("phone")
        phone.backend.addSong("m1")
        phone.backend.playlists["LPbig"] = "Antes"
        phone.backend.members += "LPbig" to "m1"
        for (i in 1..450) phone.backend.like("many$i")
        phone.connect()
        eventually("the phone sent its library", ms = 10_000) { room.head == 903L }
        // A rename gives the playlist a newer place in the room's log: after its song, two pages later.
        phone.backend.playlists["LPbig"] = "Después"; phone.changed()
        eventually("the rename is in the room") { room.value("pl:LPbig") == LibrarySyncCore.encodePlaylistName("Después") }
        // A like of a song no device ever described: it can never be placed here.
        room.receive("other", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { add(buildJsonArray { add(JsonPrimitive("lk:ghost")); add(JsonPrimitive(System.currentTimeMillis())); add(JsonPrimitive("1")) }) })
        })

        val tablet = Device("tablet")
        tablet.connect()
        eventually("the tablet placed the song once its playlist arrived", ms = 10_000) {
            tablet.backend.playlists["LPbig"] == "Después" && ("LPbig" to "m1") in tablet.backend.members
        }
        tablet.changed(); phone.changed()
        settle(); settle()
        assertEquals("the song is still in the playlist in the room", "1", room.value("pm:LPbig:m1"))
        assertTrue("and on the phone", ("LPbig" to "m1") in phone.backend.members)
        assertEquals("what cannot be placed is not removed either", "1", room.value("lk:ghost"))
    }

    @Test
    fun `a register this device cannot decode is parked, never sent back as a removal that deletes it on the device that made it`() {
        val phone = Device("phone")
        phone.backend.playlists["LPblankname"] = " " // the create dialog only rejects an EMPTY name
        phone.connect()
        eventually("the playlist is in the room") { room.value("pl:LPblankname") == LibrarySyncCore.encodePlaylistName(" ") }
        // A pin with a blank title (or from a newer version) that this build cannot draw.
        val pin = LibrarySyncCore.pinKey(LibrarySyncCore.PinRef("l", "P", "PLblank"))
        val pinValue = LibrarySyncCore.encodePin(LibrarySyncCore.PinMeta(" "))
        room.receive("newer", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { add(buildJsonArray { add(JsonPrimitive(pin)); add(JsonPrimitive(System.currentTimeMillis())); add(JsonPrimitive(pinValue)) }) })
        })

        val tablet = Device("tablet")
        tablet.connect()
        eventually("the tablet caught up") { tablet.ledger.stored.containsKey("pl:LPblankname") && tablet.ledger.stored.containsKey(pin) }
        tablet.changed(); phone.changed()
        settle(); settle()
        assertEquals("the playlist is still in the room", LibrarySyncCore.encodePlaylistName(" "), room.value("pl:LPblankname"))
        assertEquals("and on the phone that made it", " ", phone.backend.playlists["LPblankname"])
        assertEquals("the pin is still in the room", pinValue, room.value(pin))
    }

    @Test
    fun `a parked like that becomes real here is no longer parked - the user's unlike goes out and the old like does not come back`() {
        val tablet = Device("tablet")
        tablet.connect()
        settle()
        // A like of a song this device has never seen: it cannot be placed, so it is parked.
        room.receive("other", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { add(buildJsonArray { add(JsonPrimitive("lk:ghost")); add(JsonPrimitive(System.currentTimeMillis())); add(JsonPrimitive("1")) }) })
        })
        eventually("the tablet parked the like") { tablet.ledger.stored["lk:ghost"]?.parked == true }
        // The song reaches the tablet another way and the user likes it here; then changes their mind.
        tablet.backend.like("ghost"); tablet.changed()
        settle()
        tablet.backend.likes -= "ghost"; tablet.changed()
        eventually("the unlike reaches the room") { room.value("lk:ghost") == "0" }
        // Something else arrives and the tablet pulls again: the old like must not be applied again.
        room.receive("other", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { add(buildJsonArray { add(JsonPrimitive("dk:song:other")); add(JsonPrimitive(System.currentTimeMillis())); add(JsonPrimitive("1")) }) })
        })
        eventually("the tablet pulled again") { ("song" to "other") in tablet.backend.dislikes }
        settle()
        assertFalse("the unlike stands", "ghost" in tablet.backend.likes)
        assertEquals("0", room.value("lk:ghost"))
    }

    @Test
    fun `after a backup is restored the room wins over the old library, and what only the backup had is added`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        repeat(40) { phone.backend.like("r$it") }
        phone.connect(); tablet.connect()
        eventually("the tablet has all 40") { tablet.backend.likes.size == 40 }
        // The user removes 20 on the tablet, ten at a time: each step is a normal tidy-up, under the brake (also on the restored phone).
        for (step in 0 until 2) {
            for (i in step * 10 until step * 10 + 10) tablet.backend.likes -= "r$i"
            tablet.changed()
            eventually("step $step reaches the phone") { phone.backend.likes.size == 40 - (step + 1) * 10 }
        }
        phone.disconnect()

        // The phone restores an old backup (all 40 likes, plus one only the backup had); its sync memory starts empty and fresh.
        val restored = Device("phone", fresh = true)
        repeat(40) { restored.backend.like("r$it") }
        restored.backend.like("onlyInBackup")
        restored.connect()
        val kept = (20 until 40).map { "r$it" }.toSet()
        eventually("the room's removals win on the restored phone") { restored.backend.likes == kept + "onlyInBackup" }
        eventually("what only the backup had reaches the tablet") { "onlyInBackup" in tablet.backend.likes }
        settle(); settle()
        assertEquals("nothing the user removed came back", kept + "onlyInBackup", tablet.backend.likes)
        assertTrue((0 until 20).all { room.value("lk:r$it") == "0" })
    }

    @Test
    fun `a backup restored while the room holds a mass removal the other devices braked is not wiped, and brings nothing back`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        repeat(40) { phone.backend.like("b$it") }
        phone.connect(); tablet.connect()
        eventually("the tablet has all 40") { tablet.backend.likes.size == 40 }
        settle()
        // A faulty device pushes 40 removals at once: the tablet brakes them and keeps its likes.
        val now = System.currentTimeMillis()
        room.receive("faulty", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray { repeat(40) { add(buildJsonArray { add(JsonPrimitive("lk:b$it")); add(JsonPrimitive(now)); add(JsonPrimitive("0")) }) } })
        })
        settle(); settle()
        assertEquals("the tablet braked the removals", 40, tablet.backend.likes.size)
        phone.disconnect()

        // The phone restores its backup (the same 40 likes); its sync memory starts empty and fresh.
        val restored = Device("phone", fresh = true)
        repeat(40) { restored.backend.like("b$it") }
        restored.connect()
        eventually("the restored phone caught up") { restored.ledger.stored.keys.count { it.startsWith("lk:") } == 40 }
        restored.changed()
        settle(); settle()
        assertEquals("the restored library was not wiped by the room's removals", 40, restored.backend.likes.size)
        assertTrue("and the held removals were not sent back as new likes", (0 until 40).all { room.value("lk:b$it") == "0" })
        assertEquals(40, tablet.backend.likes.size)
    }
}
