package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Key
import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 536 — the library sync between a user's devices: who wins, what counts as a local change, what is held back. */
class LibrarySyncCoreTest {
    private fun reg(key: String, ts: Long, v: String = "1") = Reg(key, ts, v)

    @Test
    fun `keys round-trip and an id with a colon is never synced`() {
        assertEquals(Key.Like("abc"), LibrarySyncCore.parse(LibrarySyncCore.likeKey("abc")))
        assertEquals(Key.Dislike("song", "x1"), LibrarySyncCore.parse(LibrarySyncCore.dislikeKey("song", "x1")))
        assertEquals(Key.Dislike("softartist", "UC9"), LibrarySyncCore.parse("dk:softartist:UC9"))
        assertEquals(Key.Playlist("LPabcdefgh"), LibrarySyncCore.parse(LibrarySyncCore.playlistKey("LPabcdefgh")))
        assertEquals(Key.Member("PLxyz", "s1"), LibrarySyncCore.parse(LibrarySyncCore.memberKey("PLxyz", "s1")))
        assertEquals(Key.Song("s1"), LibrarySyncCore.parse("sg:s1"))
        assertNull(LibrarySyncCore.parse("dk:nonsense:x"))
        assertNull(LibrarySyncCore.parse("zz:what"))
        assertNull(LibrarySyncCore.parse("lk:"))
        assertNull(LibrarySyncCore.parse("pm:onlyone"))
        assertFalse(LibrarySyncCore.syncableId("local:123"))
        assertFalse(LibrarySyncCore.syncableId(""))
        assertTrue(LibrarySyncCore.syncableId("dQw4w9WgXcQ"))
    }

    @Test
    fun `the newest change wins and an exact tie settles on the larger value on every device`() {
        val known = reg("lk:a", 1000, "1")
        assertTrue(LibrarySyncCore.accepts(known, reg("lk:a", 2000, "0")))
        assertFalse(LibrarySyncCore.accepts(known, reg("lk:a", 500, "0")))
        assertFalse("the same register again is not news", LibrarySyncCore.accepts(known, reg("lk:a", 1000, "1")))
        assertTrue(LibrarySyncCore.accepts(reg("lk:a", 1000, "0"), reg("lk:a", 1000, "1")))
        assertFalse(LibrarySyncCore.accepts(reg("lk:a", 1000, "1"), reg("lk:a", 1000, "0")))
        assertTrue(LibrarySyncCore.accepts(null, reg("lk:a", 1)))
    }

    @Test
    fun `a first run sends everything it has and removes nothing`() {
        val current = mapOf("lk:a" to "1", "lk:b" to "1", "pl:LP1" to LibrarySyncCore.encodePlaylistName("Gym"))
        val ops = LibrarySyncCore.localOps(current, emptyMap(), now = 5000)
        assertEquals(setOf("lk:a", "lk:b", "pl:LP1"), ops.map { it.key }.toSet())
        assertTrue(ops.all { it.ts == 5000L && it.pending && it.value != "0" })
    }

    @Test
    fun `an unlike here becomes a zero register, strictly newer than what the ledger had`() {
        val ledger = mapOf("lk:a" to Reg("lk:a", 9_000, "1"))
        val ops = LibrarySyncCore.localOps(emptyMap(), ledger, now = 1_000) // this device's clock is BEHIND
        assertEquals(listOf(Reg("lk:a", 9_001, "0", pending = true)), ops)
    }

    @Test
    fun `nothing changed means nothing to send, and an already removed item is not removed again`() {
        val ledger = mapOf(
            "lk:a" to Reg("lk:a", 100, "1"),
            "lk:gone" to Reg("lk:gone", 100, "0"),
            "sg:a" to Reg("sg:a", 100, "{\"t\":\"x\"}"),
        )
        assertEquals(emptyList<Reg>(), LibrarySyncCore.localOps(mapOf("lk:a" to "1"), ledger, now = 500))
    }

    @Test
    fun `a playlist rename is a change, a song's metadata is sent once and never removed`() {
        val ledger = mapOf("pl:LP1" to Reg("pl:LP1", 100, LibrarySyncCore.encodePlaylistName("Gym")), "sg:s" to Reg("sg:s", 100, "{\"t\":\"A\"}"))
        val ops = LibrarySyncCore.localOps(
            mapOf("pl:LP1" to LibrarySyncCore.encodePlaylistName("Gym 2"), "sg:s" to "{\"t\":\"B\"}", "sg:new" to "{\"t\":\"N\"}"),
            ledger, now = 200,
        )
        assertEquals(setOf("pl:LP1", "sg:new"), ops.map { it.key }.toSet())
        assertTrue("sg:s was not resent", ops.none { it.key == "sg:s" })
        // metadata vanished from current (song deleted): still no tombstone
        assertTrue(LibrarySyncCore.localOps(emptyMap(), mapOf("sg:s" to Reg("sg:s", 1, "{}")), 9).isEmpty())
    }

    @Test
    fun `remote registers that lose to what this device already knows are dropped, a key repeated in one batch keeps the winner`() {
        val ledger = mapOf("lk:a" to Reg("lk:a", 5000, "1"))
        val accepted = LibrarySyncCore.acceptedFrom(
            ledger,
            listOf(reg("lk:a", 4000, "0"), reg("lk:b", 10, "1"), reg("lk:b", 20, "0"), reg("lk:b", 15, "1")),
        )
        assertEquals(listOf(reg("lk:b", 20, "0")), accepted)
    }

    @Test
    fun `a batch is decoded into the effects the adapter applies`() {
        val plan = LibrarySyncCore.plan(
            listOf(
                reg("sg:s1", 1, LibrarySyncCore.encodeSong(LibrarySyncCore.SongMeta("Título \"raro\"", "Artista", "http://c/1"))),
                reg("pl:LP1", 1, LibrarySyncCore.encodePlaylistName("Gym")),
                reg("pl:LP2", 1, "0"),
                reg("pm:LP1:s1", 1, "1"),
                reg("pm:LP1:s2", 1, "0"),
                reg("lk:s1", 1, "1"),
                reg("lk:s3", 1, "0"),
                reg("dk:song:s9", 1, "1"),
                reg("dk:artist:UC1", 1, "0"),
                reg("zz:ignored", 1, "1"),
            ),
        )
        assertEquals(listOf("s1" to LibrarySyncCore.SongMeta("Título \"raro\"", "Artista", "http://c/1")), plan.songs)
        assertEquals(listOf("LP1" to "Gym"), plan.playlistUpserts)
        assertEquals(listOf("LP2"), plan.playlistRemovals)
        assertEquals(listOf("LP1" to "s1"), plan.memberAdds)
        assertEquals(listOf("LP1" to "s2"), plan.memberRemovals)
        assertEquals(listOf("s1" to true, "s3" to false), plan.likes)
        assertEquals(listOf(Triple("song", "s9", true), Triple("artist", "UC1", false)), plan.dislikes)
        assertEquals("a playlist, a member, an unlike and an un-dislike", 4, plan.removalCount)
    }

    @Test
    fun `names and titles with quotes, backslashes and line breaks survive the trip`() {
        val tricky = "Mix \"2024\" \\ nuevo\nlínea"
        assertEquals(tricky.replace('\n', '\n'), LibrarySyncCore.decodePlaylistName(LibrarySyncCore.encodePlaylistName(tricky)))
        val meta = LibrarySyncCore.SongMeta("A\tB", "C\"D", "")
        assertEquals(meta, LibrarySyncCore.decodeSong(LibrarySyncCore.encodeSong(meta)))
        assertNull(LibrarySyncCore.decodeSong("{}"))
        assertNull(LibrarySyncCore.decodePlaylistName("{\"n\":\"  \"}"))
    }

    @Test
    fun `a mass deletion is held, a normal tidy-up is not`() {
        assertFalse(LibrarySyncCore.brakeTrips(removals = 3, known = 5))
        assertFalse("few in absolute terms", LibrarySyncCore.brakeTrips(removals = 20, known = 30))
        assertFalse("many but a small share of a big library", LibrarySyncCore.brakeTrips(removals = 60, known = 1000))
        assertTrue(LibrarySyncCore.brakeTrips(removals = 300, known = 500))
        assertTrue(LibrarySyncCore.brakeTrips(removals = 26, known = 60))
    }

    @Test
    fun `album registers parse, encode and plan as the album only`() {
        assertEquals(Key.Album("MPREb_x"), LibrarySyncCore.parse("ab:MPREb_x"))
        assertEquals(Key.AlbumMeta("MPREb_x"), LibrarySyncCore.parse("ag:MPREb_x"))
        assertTrue(LibrarySyncCore.isMeta("ag:MPREb_x"))
        val meta = LibrarySyncCore.AlbumMeta("Título \"raro\"", "http://c", "OLAKabc", 2019, 12, 3000)
        assertEquals(meta, LibrarySyncCore.decodeAlbum(LibrarySyncCore.encodeAlbum(meta)))
        assertEquals(null, LibrarySyncCore.decodeAlbum(LibrarySyncCore.encodeAlbum(meta.copy(year = null)))?.year)
        val plan = LibrarySyncCore.plan(
            listOf(Reg("ag:A", 1, LibrarySyncCore.encodeAlbum(meta)), Reg("ab:A", 2, "1"), Reg("ab:B", 3, "0")),
        )
        assertEquals(listOf("A"), plan.albums.map { it.first })
        assertEquals(listOf("A" to true, "B" to false), plan.albumSaves)
        assertTrue("no song like comes out of an album", plan.likes.isEmpty())
        assertEquals("an unsave counts for the mass-deletion brake", 1, plan.removalCount)
    }

    @Test
    fun `album details are create-only like song details`() {
        val ledger = mapOf("ag:A" to Reg("ag:A", 5, "{}"), "ab:A" to Reg("ab:A", 5, "1"))
        val ops = LibrarySyncCore.localOps(mapOf("ag:A" to "{\"t\":\"changed\"}", "ab:A" to "1"), ledger, 100)
        assertTrue(ops.isEmpty())
        val gone = LibrarySyncCore.localOps(emptyMap(), ledger, 100)
        assertEquals(listOf("ab:A"), gone.map { it.key })
    }
}
