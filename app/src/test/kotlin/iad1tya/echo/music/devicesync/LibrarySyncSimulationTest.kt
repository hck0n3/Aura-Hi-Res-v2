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
        /** Row id → the YouTube playlist it is linked to (`PlaylistEntity.browseId`); [playlists] and [members] are by row id. */
        val browseIds = HashMap<String, String>()
        /** Biblioteca ▸ Fijados, local playlists only: row id → the pin's snapshot. */
        val pins = LinkedHashMap<String, LibrarySyncCore.PinMeta>()
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

        private fun rows() = playlists.keys.map { LibrarySyncCore.PlaylistRow(it, browseIds[it]) }

        override suspend fun readLocal(playlists: LibrarySyncCore.PlaylistIds, songMetaKnown: (String) -> Boolean): Map<String, String> = synchronized(lock) {
            val facts = LinkedHashMap<String, String>()
            val referenced = LinkedHashSet<String>()
            likes.forEach { facts[LibrarySyncCore.likeKey(it)] = "1"; referenced += it }
            val keyOf = rows().associate { it.id to playlists.keyOf(it) }
            // The same rule as LibrarySyncAdapter (row 741): one row per key says the list's name and songs.
            val sources = playlists.sources(rows())
            rows().forEach { row -> playlists.linkFact(row)?.let { (k, v) -> facts[k] = v } }
            this.playlists.forEach { (id, n) -> sources[id]?.let { k -> facts[LibrarySyncCore.playlistKey(k)] = LibrarySyncCore.encodePlaylistName(n) } }
            members.forEach { (p, s) -> sources[p]?.let { k -> facts[LibrarySyncCore.memberKey(k, s)] = "1"; referenced += s } }
            pins.forEach { (p, meta) -> keyOf[p]?.let { k -> facts[LibrarySyncCore.pinKey(LibrarySyncCore.PinRef("l", "P", k))] = LibrarySyncCore.encodePin(meta) } }
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
            // The same placing rules as LibrarySyncAdapter (row 740). A list that arrives here is created with its key as its row id.
            val targets = LibrarySyncCore.PlaylistTargets(rows(), plan.before, plan.after)
            for ((k, n) in plan.playlistUpserts) {
                val all = targets.rowsOf(k)
                if (all.isNotEmpty()) all.forEach { playlists[it] = n }
                else if (targets.forAddition(k) == null) { playlists[k] = n; targets.add(LibrarySyncCore.PlaylistRow(k, null)) }
            }
            for ((row, b) in targets.unlinked()) if (b !in browseIds.values) browseIds[row] = b
            for ((p, s) in plan.memberAdds) {
                val row = targets.forAddition(p)
                if (row != null && s in songs) members += row to s else { unresolved++; unplaced += LibrarySyncCore.memberKey(p, s) }
            }
            for ((id, on) in plan.likes) if (on) { if (id in songs) likes += id else { unresolved++; unplaced += LibrarySyncCore.likeKey(id) } } else likes -= id
            for ((set, id, on) in plan.dislikes) if (on) dislikes += set to id else dislikes -= set to id
            for ((id, meta) in plan.albums) albums.putIfAbsent(id, meta)
            for ((id, on) in plan.albumSaves) if (on) { if (id in albums) savedAlbums += id else { unresolved++; unplaced += LibrarySyncCore.albumKey(id) } } else savedAlbums -= id
            for ((p, s) in plan.memberRemovals) targets.forRemoval(p).forEach { members -= it to s }
            for (p in plan.playlistRemovals) targets.forRemoval(p).forEach { row ->
                playlists -= row; browseIds -= row; pins -= row; members.removeAll { it.first == row }
            }
            for ((ref, meta) in plan.pinUpserts) {
                if (ref.scope != "l" || ref.code != "P" || !meta.local) continue
                val row = targets.forAddition(ref.id)
                if (row != null) pins[row] = meta else { unresolved++; unplaced += LibrarySyncCore.pinKey(ref) }
            }
            for (ref in plan.pinRemovals) if (ref.scope == "l" && ref.code == "P") targets.forRemoval(ref.id).forEach { pins -= it }
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

        /** [holdAnswers]: the room's answers wait in the inbox until [releaseAnswers] (a slow first pull after a reconnect). */
        fun connect(holdAnswers: Boolean = false) {
            online = true
            room.join(id, inbox)
            if (!holdAnswers) releaseAnswers()
            sync.onConnected(serverLib = 2)
        }

        fun releaseAnswers() {
            consumer = scope.launch { for (m in inbox) sync.handle(m) }
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

    /** Row 753: back in the room, a change made before the first pull has landed keeps its time too. */
    @Test
    fun `a like made while the first pull after a reconnect is on its way is not undone by an older unlike`() {
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
        // Tuesday: the tablet is back in the room and likes Y before the answer to its first pull has arrived.
        tablet.connect(holdAnswers = true)
        tablet.backend.like("Y"); tablet.changed()
        settle()
        tablet.releaseAnswers()
        eventually("the tablet's newer like reaches the room") { room.value("lk:Y") == "1" }
        eventually("and the phone") { "Y" in phone.backend.likes }
        settle()
        assertTrue("the tablet kept its like", "Y" in tablet.backend.likes)
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

    // Row 740: a local list that LibraryUploadSync links to the YouTube account gets a browseId. It used to travel under that browseId
    // from then on: the other devices saw «list deleted, new list created», and with a big list the brake kept the deletion = two lists.

    private fun Device.listWithSongs(id: String, name: String, songs: Int, prefix: String) {
        backend.playlists[id] = name
        for (i in 1..songs) { backend.addSong("$prefix$i"); backend.members += id to "$prefix$i" }
    }

    private fun Device.songsOf(row: String) = backend.members.filter { it.first == row }.map { it.second }.toSet()

    @Test
    fun `a list linked to YouTube on one device stays one list on the other, with its songs and its pin`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPgymgymgy", "Gym", 5, "g")
        phone.backend.pins["LPgymgymgy"] = LibrarySyncCore.PinMeta("Gym", local = true, at = 1L)
        phone.connect(); tablet.connect()
        eventually("the tablet has the list, its songs and its pin") { tablet.songsOf("LPgymgymgy").size == 5 && "LPgymgymgy" in tablet.backend.pins }
        settle()
        val head = room.head

        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.changed() // LibraryUploadSync linked it
        eventually("the link reached the room") { room.head > head }
        settle(); settle()
        assertEquals("one list on the tablet", setOf("LPgymgymgy"), tablet.backend.playlists.keys)
        assertEquals((1..5).map { "g$it" }.toSet(), tablet.songsOf("LPgymgymgy"))
        assertEquals("its pin", setOf("LPgymgymgy"), tablet.backend.pins.keys)
        assertEquals("and one on the phone", setOf("LPgymgymgy"), phone.backend.playlists.keys)
        assertEquals("nothing was removed in the room", LibrarySyncCore.encodePlaylistName("Gym"), room.value("pl:LPgymgymgy"))
        assertTrue((1..5).all { room.value("pm:LPgymgymgy:g$it") == "1" })
        assertEquals(LibrarySyncCore.encodePin(LibrarySyncCore.PinMeta("Gym", local = true, at = 1L)), room.value("pn:l:P:LPgymgymgy"))
        assertEquals("no second key for the same list", null, room.value("pl:PLgymOnYouTube"))
        eventually("the tablet learned the link") { tablet.backend.browseIds["LPgymgymgy"] == "PLgymOnYouTube" }
    }

    @Test
    fun `a big list linked on one device is never left duplicated by the mass-removal brake on the other`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPbigbigbig", "Todo", 60, "b")
        phone.connect(); tablet.connect()
        eventually("the tablet has the 60 songs") { tablet.songsOf("LPbigbigbig").size == 60 }
        settle()
        val head = room.head

        phone.backend.browseIds["LPbigbigbig"] = "PLtodo"; phone.changed()
        eventually("the link reached the room") { room.head > head }
        settle(); settle()
        assertEquals("one list, not two", setOf("LPbigbigbig"), tablet.backend.playlists.keys)
        assertEquals(60, tablet.songsOf("LPbigbigbig").size)
        assertTrue("no removal was ever held", tablet.ledger.stored.values.none { LibrarySyncCore.isHeld(it) })
        assertTrue("the room still has every song of the list", (1..60).all { room.value("pm:LPbigbigbig:b$it") == "1" })
    }

    @Test
    fun `both devices linking the same list to the same YouTube playlist leave one list and nothing removed`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPsamesame", "Misma", 30, "m")
        phone.connect(); tablet.connect()
        eventually("the tablet has the list") { tablet.songsOf("LPsamesame").size == 30 }
        settle()
        // Both link it while away from the room (each device's own LibraryUploadSync found the same playlist on the account).
        phone.disconnect(); tablet.disconnect()
        phone.backend.browseIds["LPsamesame"] = "PLmisma"; phone.changed()
        tablet.backend.browseIds["LPsamesame"] = "PLmisma"; tablet.changed()
        settle()
        phone.connect(); tablet.connect()
        settle(); settle()
        for (d in listOf(phone, tablet)) {
            assertEquals(setOf("LPsamesame"), d.backend.playlists.keys)
            assertEquals(30, d.songsOf("LPsamesame").size)
        }
        assertEquals(LibrarySyncCore.encodePlaylistName("Misma"), room.value("pl:LPsamesame"))
        assertTrue((1..30).all { room.value("pm:LPsamesame:m$it") == "1" })
        assertEquals(null, room.value("pl:PLmisma"))
        val head = room.head
        phone.changed(); tablet.changed()
        settle(); settle()
        assertEquals("and nothing echoes afterwards", head, room.head)
    }

    @Test
    fun `both devices linking the same list to two different YouTube playlists still leave one list, and nothing ping-pongs`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPracerace", "Carrera", 5, "r")
        phone.connect(); tablet.connect()
        eventually("the tablet has the list") { tablet.songsOf("LPracerace").size == 5 }
        settle()
        phone.disconnect(); tablet.disconnect()
        phone.backend.browseIds["LPracerace"] = "PLfromPhone"; phone.changed()
        tablet.backend.browseIds["LPracerace"] = "PLfromTablet"; tablet.changed()
        settle()
        phone.connect(); tablet.connect()
        settle(); settle()
        for (d in listOf(phone, tablet)) assertEquals(setOf("LPracerace"), d.backend.playlists.keys)
        assertEquals("each device keeps the link it made", "PLfromPhone", phone.backend.browseIds["LPracerace"])
        assertEquals("PLfromTablet", tablet.backend.browseIds["LPracerace"])
        assertEquals(LibrarySyncCore.encodePlaylistName("Carrera"), room.value("pl:LPracerace"))
        assertTrue(room.value("pb:LPracerace") in setOf("PLfromPhone", "PLfromTablet"))
        val head = room.head
        phone.changed(); tablet.changed()
        settle(); settle()
        assertEquals("the two links do not fight", head, room.head)
    }

    @Test
    fun `a device that already has the YouTube copy of a linked list gets no second one, and its extra song reaches the other`() {
        val phone = Device("phone")
        phone.listWithSongs("LPgymgymgy", "Gym", 5, "g")
        phone.connect()
        settle()
        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.changed()
        eventually("the link is in the room") { room.value("pb:LPgymgymgy") == "PLgymOnYouTube" }
        settle()
        // The tablet downloaded that YouTube playlist on its own (its YouTube sync), under an id of its own, before joining the room.
        val tablet = Device("tablet")
        tablet.backend.playlists["LPdownload"] = "Gym"; tablet.backend.browseIds["LPdownload"] = "PLgymOnYouTube"
        for (s in listOf("g1", "g2", "extra")) { tablet.backend.addSong(s); tablet.backend.members += "LPdownload" to s }
        tablet.connect()
        eventually("the phone gets the tablet's extra song") { "extra" in phone.songsOf("LPgymgymgy") }
        settle(); settle()
        assertEquals("one list on the tablet, its own", setOf("LPdownload"), tablet.backend.playlists.keys)
        assertEquals((1..5).map { "g$it" }.toSet() + "extra", tablet.songsOf("LPdownload"))
        assertEquals(setOf("LPgymgymgy"), phone.backend.playlists.keys)
        assertEquals("the list kept its key", null, room.value("pl:PLgymOnYouTube"))
    }

    @Test
    fun `an older version moving a linked list to its browseId deletes nothing here`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPgymgymgy", "Gym", 6, "g")
        phone.backend.pins["LPgymgymgy"] = LibrarySyncCore.PinMeta("Gym", local = true, at = 1L)
        phone.connect(); tablet.connect()
        eventually("the tablet has the list") { tablet.songsOf("LPgymgymgy").size == 6 && "LPgymgymgy" in tablet.backend.pins }
        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.changed()
        eventually("the tablet learned the link") { tablet.backend.browseIds["LPgymgymgy"] == "PLgymOnYouTube" }
        settle()
        // A third device on an older build links its copy and moves it the old way: every register of the old key off, all under the new one.
        val now = System.currentTimeMillis() + 1_000
        fun op(key: String, v: String) = buildJsonArray { add(JsonPrimitive(key)); add(JsonPrimitive(now)); add(JsonPrimitive(v)) }
        room.receive("old", buildJsonObject {
            put("t", "lib_push")
            put("ops", buildJsonArray {
                add(op("pl:PLgymOnYouTube", LibrarySyncCore.encodePlaylistName("Gym")))
                for (i in 1..6) add(op("pm:PLgymOnYouTube:g$i", "1"))
                add(op("pn:l:P:PLgymOnYouTube", LibrarySyncCore.encodePin(LibrarySyncCore.PinMeta("Gym", local = true, at = 1L))))
                add(op("pl:LPgymgymgy", "0"))
                for (i in 1..6) add(op("pm:LPgymgymgy:g$i", "0"))
                add(op("pn:l:P:LPgymgymgy", "0"))
            })
        })
        settle(); settle(); settle()
        for (d in listOf(phone, tablet)) {
            assertEquals(setOf("LPgymgymgy"), d.backend.playlists.keys)
            assertEquals(6, d.songsOf("LPgymgymgy").size)
            assertEquals(setOf("LPgymgymgy"), d.backend.pins.keys)
            assertTrue("kept as a change of name, not by the brake", d.ledger.stored.values.none { LibrarySyncCore.isHeld(it) })
        }
        assertEquals("the list lives on under the browseId", LibrarySyncCore.encodePlaylistName("Gym"), room.value("pl:PLgymOnYouTube"))
        assertTrue((1..6).all { room.value("pm:PLgymOnYouTube:g$it") == "1" })
        assertEquals("0", room.value("pl:LPgymgymgy"))
        val head = room.head
        phone.changed(); tablet.changed()
        settle(); settle()
        assertEquals("and it settles there", head, room.head)
    }

    @Test
    fun `a list linked before it ever went to the room travels under its browseId only`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPnewnewnw", "Nueva", 3, "n")
        phone.backend.browseIds["LPnewnewnw"] = "PLnueva"
        phone.connect(); tablet.connect()
        eventually("the tablet has it") { tablet.songsOf("PLnueva").size == 3 }
        assertEquals(null, room.value("pl:LPnewnewnw"))
        assertEquals(null, room.value("pb:LPnewnewnw"))
    }

    @Test
    fun `a song removed from a linked list, and then the list deleted, still go away on the other device`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPgymgymgy", "Gym", 5, "g")
        phone.backend.pins["LPgymgymgy"] = LibrarySyncCore.PinMeta("Gym", local = true, at = 1L)
        phone.connect(); tablet.connect()
        eventually("the tablet has the list") { tablet.songsOf("LPgymgymgy").size == 5 && "LPgymgymgy" in tablet.backend.pins }
        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.changed()
        eventually("the tablet learned the link") { tablet.backend.browseIds["LPgymgymgy"] == "PLgymOnYouTube" }
        settle()

        tablet.backend.members -= "LPgymgymgy" to "g1"; tablet.changed()
        eventually("the removal reaches the phone") { "g1" !in phone.songsOf("LPgymgymgy") }
        phone.backend.pins.clear(); phone.changed()
        eventually("the unpin reaches the tablet") { tablet.backend.pins.isEmpty() }
        phone.backend.playlists.clear(); phone.backend.members.clear(); phone.backend.browseIds.clear(); phone.changed()
        eventually("the deletion reaches the tablet") { tablet.backend.playlists.isEmpty() && tablet.backend.members.isEmpty() }
        settle()
        assertEquals("0", room.value("pl:LPgymgymgy"))
        assertEquals("and does not come back under the browseId", null, room.value("pl:PLgymOnYouTube"))
    }

    @Test
    fun `a restored backup does not bring back a linked list another device deleted, nor a song removed from one`() {
        val phone = Device("phone")
        phone.listWithSongs("LPgymgymgy", "Gym", 5, "g")
        phone.listWithSongs("LPrunrunru", "Run", 4, "r")
        phone.connect()
        settle()
        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.backend.browseIds["LPrunrunru"] = "PLrunOnYouTube"; phone.changed()
        eventually("the links are in the room") { room.value("pb:LPgymgymgy") == "PLgymOnYouTube" && room.value("pb:LPrunrunru") == "PLrunOnYouTube" }
        settle()
        // A backup taken now, on another device: both lists, linked, with their songs.
        val tablet = Device("tablet", fresh = true)
        tablet.listWithSongs("LPgymgymgy", "Gym", 5, "g"); tablet.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"
        tablet.listWithSongs("LPrunrunru", "Run", 4, "r"); tablet.backend.browseIds["LPrunrunru"] = "PLrunOnYouTube"
        // Since then the phone deleted one list and took a song out of the other.
        phone.backend.playlists -= "LPgymgymgy"; phone.backend.browseIds -= "LPgymgymgy"; phone.backend.members.removeAll { it.first == "LPgymgymgy" }
        phone.backend.members -= "LPrunrunru" to "r1"
        phone.changed()
        eventually("the phone's changes are in the room") { room.value("pl:LPgymgymgy") == "0" && room.value("pm:LPrunrunru:r1") == "0" }
        settle()

        tablet.connect() // the restore emptied its ledger: it reads the room from the start
        eventually("the room wins on the restored tablet") {
            tablet.backend.playlists.keys == setOf("LPrunrunru") && tablet.songsOf("LPrunrunru") == setOf("r2", "r3", "r4")
        }
        settle(); settle()
        assertTrue(tablet.songsOf("LPgymgymgy").isEmpty())
        assertEquals("the deleted list did not come back", "0", room.value("pl:LPgymgymgy"))
        assertEquals("nor under its browseId", null, room.value("pl:PLgymOnYouTube"))
        assertEquals("the removed song did not come back", "0", room.value("pm:LPrunrunru:r1"))
        assertEquals(setOf("LPrunrunru"), phone.backend.playlists.keys)
        assertEquals(setOf("r2", "r3", "r4"), phone.songsOf("LPrunrunru"))
    }

    @Test
    fun `a song removed from a list still travels when this device also has its own YouTube copy of it`() {
        val phone = Device("phone"); val tablet = Device("tablet")
        phone.listWithSongs("LPgymgymgy", "Gym", 5, "g")
        phone.connect(); tablet.connect()
        eventually("the tablet has the list") { tablet.songsOf("LPgymgymgy").size == 5 }
        settle()
        tablet.disconnect()
        phone.backend.browseIds["LPgymgymgy"] = "PLgymOnYouTube"; phone.changed()
        eventually("the link is in the room") { room.value("pb:LPgymgymgy") == "PLgymOnYouTube" }
        // Before it learns the link, the tablet's own YouTube sync downloads that playlist as a second row.
        tablet.backend.playlists["LPdownload"] = "Gym (YouTube)"; tablet.backend.browseIds["LPdownload"] = "PLgymOnYouTube"
        for (i in 1..5) tablet.backend.members += "LPdownload" to "g$i"
        tablet.changed()
        tablet.connect()
        settle(); settle()
        assertEquals("both rows are still here", setOf("LPgymgymgy", "LPdownload"), tablet.backend.playlists.keys)
        assertEquals("one list on the phone", setOf("LPgymgymgy"), phone.backend.playlists.keys)
        assertEquals((1..5).map { "g$it" }.toSet(), phone.songsOf("LPgymgymgy"))
        assertEquals("the list travels under one name, the one of the row whose id is its key", LibrarySyncCore.encodePlaylistName("Gym"), room.value("pl:LPgymgymgy"))

        tablet.backend.members -= "LPgymgymgy" to "g3"; tablet.changed()
        eventually("the removal reaches the phone although the YouTube copy still has the song") { "g3" !in phone.songsOf("LPgymgymgy") }
        assertEquals("0", room.value("pm:LPgymgymgy:g3"))
        val head = room.head
        phone.changed(); tablet.changed()
        settle(); settle()
        assertEquals("nothing echoes afterwards", head, room.head)
    }
}
