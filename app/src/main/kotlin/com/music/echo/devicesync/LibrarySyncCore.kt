package iad1tya.echo.music.devicesync

/**
 * Row 536 — the pure rules of the library sync (likes, dislikes, playlists) between a user's devices, through the Worker.
 *
 * Every synced fact is a REGISTER: a key, the time of its last change, and a value, where the value "0" means "gone". The
 * newest change wins ([accepts]); a tie goes to the larger value so every device settles on the same answer. Nothing here
 * touches Android, the database or the network, so every rule is pinned by a plain JVM test.
 *
 * Keys (no colon inside an id; an id that has one is simply not synced):
 *  · `lk:<songId>`              liked song                      value "1" / "0"
 *  · `dk:<kind>:<id>`           disliked song/artist/album/playlist and the two «menos de esto» sets   "1" / "0"
 *  · `pl:<playlistKey>`         a playlist and its name         value `{"n":"name"}` / "0"
 *  · `pm:<playlistKey>:<songId>` a song in a playlist           "1" / "0"
 *  · `sg:<songId>`              title / artist / cover of a song the other device may never have seen; create-only
 *  · `ab:<albumId>`             an album saved to the library (the heart on the album page)   "1" / "0" — NOT its songs' likes
 *  · `ag:<albumId>`             title / cover / year / size of a saved album the other device may never have seen; create-only
 *  · `pn:<scope>:<code>:<id>`   something pinned: scope `l` = Biblioteca ▸ Fijados, `h` = «Fijadas en inicio»; the code is the kind
 *                               (P playlist, A album, R artist, S song); value = the snapshot to draw it ({"t","s","c","l","x","at"}) / "0"
 *  · `hv:<songId>:<second>`     one listen in the History screen (the second is the wall-clock time read as UTC, so every device
 *                               rebuilds the same moment); create-only, value "1", never removed (clearing the history is local)
 *  · `pb:<playlistKey>`         the YouTube browseId a list that travels under its own id was linked to; create-only (row 740)
 * The playlist key is the playlist's `browseId` when it has one (the same on every device), else its own id — and a list keeps the
 * key it first travelled under for its whole life ([PlaylistIds], row 740).
 */
object LibrarySyncCore {
    const val ON = "1"
    const val OFF = "0"

    /**
     * One register as it travels and as the local ledger remembers it. [pending] = not yet acknowledged by the room. [parked] = taken
     * from the room but not placed here yet (its playlist or song has not arrived): retried later, never sent back as a removal.
     */
    data class Reg(val key: String, val ts: Long, val value: String, val pending: Boolean = false, val parked: Boolean = false)

    /** A removal the mass-deletion brake kept from this device: the room's "0", parked, so the copy here is neither deleted nor sent back as new. */
    fun isHeld(reg: Reg) = reg.parked && reg.value == OFF

    enum class Kind { LIKE, DISLIKE, PLAYLIST, MEMBER, SONG, ALBUM, ALBUM_META, PLAY, PIN, PLAYLIST_LINK }

    sealed interface Key {
        val kind: Kind
        data class Like(val songId: String) : Key { override val kind get() = Kind.LIKE }
        data class Dislike(val set: String, val id: String) : Key { override val kind get() = Kind.DISLIKE }
        data class Playlist(val playlistKey: String) : Key { override val kind get() = Kind.PLAYLIST }
        data class Member(val playlistKey: String, val songId: String) : Key { override val kind get() = Kind.MEMBER }
        data class Song(val songId: String) : Key { override val kind get() = Kind.SONG }
        data class Album(val albumId: String) : Key { override val kind get() = Kind.ALBUM }
        data class AlbumMeta(val albumId: String) : Key { override val kind get() = Kind.ALBUM_META }
        data class Play(val songId: String, val second: Long) : Key { override val kind get() = Kind.PLAY }
        data class Pin(val ref: PinRef) : Key { override val kind get() = Kind.PIN }
        data class PlaylistLink(val playlistKey: String) : Key { override val kind get() = Kind.PLAYLIST_LINK }
    }

    /** Where a pin lives: `l` = Biblioteca ▸ Fijados, `h` = «Fijadas en inicio». */
    val PIN_SCOPES = setOf("l", "h")

    /** One pinned thing, by where it is pinned, what kind it is (P/A/R/S) and its id. */
    data class PinRef(val scope: String, val code: String, val id: String)

    /** What another device needs to draw a pin it never made: the snapshot of the item. */
    data class PinMeta(
        val title: String,
        val subtitle: String = "",
        val cover: String = "",
        val local: Boolean = false,
        val extra: String = "",
        val at: Long = 0L,
    )

    /** The six dislike sets of `DislikeStore`, by their short names in the key. */
    val DISLIKE_SETS = listOf("song", "artist", "album", "playlist", "softsong", "softartist")

    fun likeKey(songId: String) = "lk:$songId"
    fun dislikeKey(set: String, id: String) = "dk:$set:$id"
    fun playlistKey(playlistKey: String) = "pl:$playlistKey"
    fun memberKey(playlistKey: String, songId: String) = "pm:$playlistKey:$songId"
    fun songKey(songId: String) = "sg:$songId"
    fun albumKey(albumId: String) = "ab:$albumId"
    fun albumMetaKey(albumId: String) = "ag:$albumId"
    fun playKey(songId: String, second: Long) = "hv:$songId:$second"
    fun pinKey(ref: PinRef) = "pn:${ref.scope}:${ref.code}:${ref.id}"
    fun playlistLinkKey(playlistKey: String) = "pb:$playlistKey"

    /** `sg:` and `ag:` registers only describe something so another device can show it: sent once, never changed, never removed. */
    fun isMeta(key: String) = key.startsWith("sg:") || key.startsWith("ag:") || key.startsWith("hv:") || key.startsWith("pb:")

    /** True when an id can be part of a key (non-empty, no colon, not absurdly long). */
    fun syncableId(id: String) = id.isNotEmpty() && ':' !in id && id.length <= 80

    fun parse(key: String): Key? {
        val colon = key.indexOf(':')
        if (colon < 0) return null
        val rest = key.substring(colon + 1)
        return when (key.substring(0, colon)) {
            "lk" -> rest.takeIf { it.isNotEmpty() }?.let(Key::Like)
            "sg" -> rest.takeIf { it.isNotEmpty() }?.let(Key::Song)
            "ab" -> rest.takeIf { it.isNotEmpty() && ':' !in it }?.let(Key::Album)
            "ag" -> rest.takeIf { it.isNotEmpty() && ':' !in it }?.let(Key::AlbumMeta)
            "hv" -> {
                val i = rest.lastIndexOf(':')
                val second = if (i <= 0) null else rest.substring(i + 1).toLongOrNull()
                if (second == null || second < 0 || ':' in rest.substring(0, i)) null else Key.Play(rest.substring(0, i), second)
            }
            "pl" -> rest.takeIf { it.isNotEmpty() && ':' !in it }?.let(Key::Playlist)
            "pb" -> rest.takeIf { it.isNotEmpty() && ':' !in it }?.let(Key::PlaylistLink)
            "pn" -> {
                // `l:P:<id>`: scope, code, then the id (no colon).
                if (rest.length < 5 || rest[1] != ':' || rest[3] != ':') null
                else {
                    val ref = PinRef(rest.substring(0, 1), rest.substring(2, 3), rest.substring(4))
                    if (ref.scope in PIN_SCOPES && ref.id.isNotEmpty() && ':' !in ref.id) Key.Pin(ref) else null
                }
            }
            "dk" -> {
                val i = rest.indexOf(':')
                if (i <= 0 || i == rest.lastIndex) null
                else rest.substring(0, i).takeIf { it in DISLIKE_SETS }?.let { Key.Dislike(it, rest.substring(i + 1)) }
            }
            "pm" -> {
                val i = rest.indexOf(':')
                if (i <= 0 || i == rest.lastIndex) null else Key.Member(rest.substring(0, i), rest.substring(i + 1))
            }
            else -> null
        }
    }

    /** Does [incoming] replace what the ledger knows? Newer wins; an exact tie goes to the larger value (the same on every device). */
    fun accepts(known: Reg?, incoming: Reg): Boolean =
        known == null || incoming.ts > known.ts || (incoming.ts == known.ts && incoming.value > known.value)

    /**
     * What changed HERE since the ledger was last written. [current] is what the database / the dislike store say now (only the "on"
     * facts: key → value). A fact that is in [ledger] as present but missing from [current] was removed here → a "0" register.
     * `sg:` registers are create-only: they are sent once and never changed or removed. The new time is strictly greater than the
     * one the ledger holds, so the room never discards a deliberate change as «old» because a clock differs.
     */
    fun localOps(current: Map<String, String>, ledger: Map<String, Reg>, now: Long): List<Reg> {
        val ops = ArrayList<Reg>()
        val linked = ArrayList<String>()
        for ((key, value) in current) {
            val known = ledger[key]
            if (isMeta(key)) {
                if (known == null) {
                    ops += Reg(key, now, value, pending = true)
                    if (key.startsWith("pb:")) linked += playlistKey(key.substring(3))
                }
                continue
            }
            if (known != null && isHeld(known)) continue
            if (known == null || known.value != value) ops += Reg(key, maxOf(now, (known?.ts ?: 0L) + 1), value, pending = true)
        }
        for ((key, known) in ledger) {
            if (isMeta(key) || known.value == OFF || key in current || parse(key) == null || known.parked) continue
            ops += Reg(key, maxOf(now, known.ts + 1), OFF, pending = true)
        }
        // Row 740: a new link goes out before the list it names (`pb:` ranks first), and the list's unchanged name once more after it,
        // so a device that reads the room from the start knows the link before it places the list. Only one tick newer than what this
        // device knows, so any real change another device made in the meantime still wins.
        for (key in linked) {
            val known = ledger[key] ?: continue
            if (current[key] != known.value || isHeld(known) || ops.any { it.key == key }) continue
            ops += Reg(key, known.ts + 1, known.value, pending = true)
        }
        return ops
    }

    /** The registers of [incoming] that win over [ledger], in arrival order (a key sent twice in one batch keeps the winner). */
    fun acceptedFrom(ledger: Map<String, Reg>, incoming: List<Reg>): List<Reg> {
        val working = HashMap<String, Reg>()
        for (reg in incoming) {
            val known = working[reg.key] ?: ledger[reg.key]
            if (accepts(known, reg)) working[reg.key] = reg
        }
        return incoming.filter { working[it.key] == it }.distinctBy { it.key }
    }

    /** The decoded effect of a batch of accepted registers, grouped so the adapter can apply them in a safe order. */
    data class Plan(
        val songs: List<Pair<String, SongMeta>>,
        val playlistUpserts: List<Pair<String, String>>,
        val playlistRemovals: List<String>,
        val memberAdds: List<Pair<String, String>>,
        val memberRemovals: List<Pair<String, String>>,
        val likes: List<Pair<String, Boolean>>,
        val dislikes: List<Triple<String, String, Boolean>>,
        val albums: List<Pair<String, AlbumMeta>> = emptyList(),
        val albumSaves: List<Pair<String, Boolean>> = emptyList(),
        val plays: List<Pair<String, Long>> = emptyList(),
        val pinUpserts: List<Pair<PinRef, PinMeta>> = emptyList(),
        val pinRemovals: List<PinRef> = emptyList(),
        val undecoded: Set<String> = emptySet(),
        /** Row 740: what the ledger said about the playlists before this batch, and what it says with the batch in it. */
        val before: PlaylistIds = PlaylistIds.EMPTY,
        val after: PlaylistIds = PlaylistIds.EMPTY,
    ) {
        val removalCount: Int
            get() = playlistRemovals.size + memberRemovals.size + likes.count { !it.second } + dislikes.count { !it.third } +
                albumSaves.count { !it.second } + pinRemovals.size
    }

    data class SongMeta(val title: String, val artist: String, val cover: String)

    /** What another device needs to show an album it has never opened: the album page itself loads the songs from YouTube. */
    data class AlbumMeta(val title: String, val cover: String, val playlistId: String, val year: Int?, val songCount: Int, val duration: Int)

    /**
     * Row 740: with [after], a removal under a playlist key whose list lives on under another of its names (an older version moved a
     * linked list to its browseId, or another name of the same list is being retired) is not a deletion: it is left out of the plan.
     */
    fun plan(accepted: List<Reg>, before: PlaylistIds = PlaylistIds.EMPTY, after: PlaylistIds = PlaylistIds.EMPTY): Plan {
        val songs = ArrayList<Pair<String, SongMeta>>()
        val upserts = ArrayList<Pair<String, String>>()
        val plRemovals = ArrayList<String>()
        val adds = ArrayList<Pair<String, String>>()
        val memberRemovals = ArrayList<Pair<String, String>>()
        val likes = ArrayList<Pair<String, Boolean>>()
        val dislikes = ArrayList<Triple<String, String, Boolean>>()
        val albums = ArrayList<Pair<String, AlbumMeta>>()
        val albumSaves = ArrayList<Pair<String, Boolean>>()
        val plays = ArrayList<Pair<String, Long>>()
        val pinUpserts = ArrayList<Pair<PinRef, PinMeta>>()
        val pinRemovals = ArrayList<PinRef>()
        val undecoded = HashSet<String>()
        for (reg in accepted) {
            when (val k = parse(reg.key)) {
                is Key.Song -> decodeSong(reg.value)?.let { songs += k.songId to it }
                is Key.AlbumMeta -> decodeAlbum(reg.value)?.let { albums += k.albumId to it }
                is Key.Album -> if (reg.value == ON || reg.value == OFF) albumSaves += k.albumId to (reg.value == ON) else undecoded += reg.key
                is Key.Play -> if (reg.value == ON) plays += k.songId to k.second
                is Key.Pin ->
                    // Only an app id is a local list's old name: a browseId may also be the pin of the YouTube playlist itself.
                    if (reg.value == OFF) { if (k.ref.code != "P" || !isOwnPlaylistId(k.ref.id) || !after.renamed(k.ref.id)) pinRemovals += k.ref }
                    else decodePin(reg.value)?.let { pinUpserts += k.ref to it } ?: run { undecoded += reg.key }
                is Key.Playlist ->
                    if (reg.value == OFF) { if (!after.renamed(k.playlistKey)) plRemovals += k.playlistKey }
                    else decodePlaylistName(reg.value)?.let { upserts += k.playlistKey to it } ?: run { undecoded += reg.key }
                is Key.Member ->
                    if (reg.value == ON) adds += k.playlistKey to k.songId
                    else if (reg.value == OFF) { if (!after.renamed(k.playlistKey)) memberRemovals += k.playlistKey to k.songId }
                    else undecoded += reg.key
                is Key.Like -> if (reg.value == ON || reg.value == OFF) likes += k.songId to (reg.value == ON) else undecoded += reg.key
                is Key.Dislike -> if (reg.value == ON || reg.value == OFF) dislikes += Triple(k.set, k.id, reg.value == ON) else undecoded += reg.key
                is Key.PlaylistLink -> Unit // read through [after] (row 740)
                null -> Unit
            }
        }
        return Plan(
            songs, upserts, plRemovals, adds, memberRemovals, likes, dislikes, albums, albumSaves, plays, pinUpserts, pinRemovals, undecoded,
            before, after,
        )
    }

    // ── playlist identity (row 740) ────────────────────────────────────────────────────────────────────────────────────────────

    /** One of the user's own playlists on this device: its row id and the YouTube playlist it is linked to, if any. */
    data class PlaylistRow(val id: String, val browseId: String?)

    /** `LP` + 8 letters is how this app names a playlist that lives only on the device; anything else is a YouTube browseId. */
    fun isOwnPlaylistId(key: String) = key.length == 10 && key.startsWith("LP") && key.drop(2).all { it.isLetter() }

    /**
     * Row 740 — the names a playlist has in the room and the one it travels under. A list keeps the key it first travelled under
     * for its whole life: being linked to YouTube adds the browseId as ANOTHER name of the same list (`pb:<key>` = browseId) instead
     * of moving the list to a new key, which every other device read as «list deleted, new list created» (and, with a big list, the
     * mass-removal brake kept the deletion: two lists). Every device picks the same key for the same list from what the room says:
     * among its names that are live in the room, an app id (`LP…`) first, then the smallest. Built from the ledger only.
     */
    class PlaylistIds private constructor(private val lists: Map<String, Reg>, private val links: Map<String, String>) {
        private val linkedTo: Map<String, List<String>> = links.entries.groupBy({ it.value }, { it.key })

        /** The room has the list [key] (or this device has it waiting to be sent). */
        fun live(key: String) = lists[key]?.let { it.value != OFF } == true

        /** The ledger has a `pl:` register for [key], whatever its value: the list has travelled under that key. */
        fun known(key: String) = lists.containsKey(key)

        /** The browseId another device (or this one) linked the list [key] to. */
        fun link(key: String): String? = links[key]

        /** [name], the browseId it is linked to, and every other key linked to that same browseId. */
        fun group(name: String): Set<String> {
            val browseId = links[name] ?: name
            return linkedSetOf(name, browseId).apply { linkedTo[browseId]?.let(::addAll) }
        }

        fun names(row: PlaylistRow): Set<String> = group(row.id) + (row.browseId?.let(::group) ?: emptySet())

        /** The key every device picks among [names]: a live app id first, then the smallest live name; null when none is live. */
        fun canonical(names: Collection<String>): String? {
            val live = names.filter(::live)
            return live.filter(::isOwnPlaylistId).minOrNull() ?: live.minOrNull()
        }

        /** The key [row] travels under. A held removal keeps its own id (it is not sent back as new under another name). */
        fun keyOf(row: PlaylistRow): String =
            canonical(names(row)) ?: row.id.takeIf { lists[it]?.let(::isHeld) == true } ?: row.browseId ?: row.id

        /** The list [key] named lives on under another of its names: a removal under [key] is a change of name, not a deletion. */
        fun renamed(key: String): Boolean = canonical(group(key)).let { it != null && it != key }

        /** The `pb:` fact [row] contributes: its link, when the list has travelled under the row's own id. */
        fun linkFact(row: PlaylistRow): Pair<String, String>? {
            val browseId = row.browseId ?: return null
            if (!known(row.id) || !syncableId(row.id) || !syncableId(browseId)) return null
            return playlistLinkKey(row.id) to browseId
        }

        companion object {
            /** Nothing known: every row travels under its browseId, else its id (the rule before row 740). */
            val EMPTY = PlaylistIds(emptyMap(), emptyMap())

            /** From the ledger, with [accepted] written over it (the state after a batch). */
            fun of(ledger: Map<String, Reg>, accepted: Collection<Reg> = emptyList()): PlaylistIds {
                val lists = HashMap<String, Reg>()
                val links = HashMap<String, String>()
                for (reg in ledger.values.asSequence() + accepted.asSequence()) {
                    when {
                        reg.key.startsWith("pl:") -> lists[reg.key.substring(3)] = reg
                        reg.key.startsWith("pb:") ->
                            reg.key.substring(3).takeIf { syncableId(it) && syncableId(reg.value) }?.let { links[it] = reg.value }
                    }
                }
                return PlaylistIds(lists, links)
            }
        }
    }

    /**
     * Row 740 — where the playlist registers of one batch land among this device's own playlists. An addition under a key goes to
     * the rows whose key it is after the batch (or, failing that, to a row that has it as another name of the same list); a removal
     * under a key reaches only the rows whose key it was before the batch and that do not live on under another of their names. So a
     * register never reaches a row that travels under a different key, whatever the row's id or browseId.
     */
    class PlaylistTargets(rows: List<PlaylistRow>, private val before: PlaylistIds, private val after: PlaylistIds) {
        private val rows = ArrayList<PlaylistRow>()
        private val beforeKey = HashMap<String, String>()
        private val afterKey = HashMap<String, String>()
        private val afterNames = HashMap<String, Set<String>>()
        private val afterCanonical = HashMap<String, String?>()

        /** The ids of the rows, for a caller that must tell them from rows outside this set. */
        val ids = HashSet<String>()

        init {
            rows.forEach(::add)
        }

        /** A row of this batch (or created by it). Everything a lookup needs is worked out once here, not per register. */
        fun add(row: PlaylistRow) {
            rows += row
            ids += row.id
            beforeKey[row.id] = before.keyOf(row)
            afterKey[row.id] = after.keyOf(row)
            val names = after.names(row)
            afterNames[row.id] = names
            afterCanonical[row.id] = after.canonical(names)
        }

        /** Every row whose key is [key] after the batch, the one whose id IS the key first. */
        fun rowsOf(key: String): List<String> = rows.filter { afterKey[it.id] == key }.map { it.id }.sortedBy { if (it == key) 0 else 1 }

        /** Where an addition under [key] goes; null when no list here has that name. */
        fun forAddition(key: String): String? = rowsOf(key).firstOrNull() ?: rows.firstOrNull { key in afterNames.getValue(it.id) }?.id

        /** The rows a removal under [key] reaches. */
        fun forRemoval(key: String): List<String> = rows.filter { row ->
            beforeKey[row.id] == key && afterCanonical[row.id].let { it == null || it == key }
        }.map { it.id }

        /** Rows with no YouTube link whose list was linked on another device: row id → browseId. */
        fun unlinked(): List<Pair<String, String>> =
            rows.mapNotNull { row -> if (row.browseId != null) null else after.link(row.id)?.let { row.id to it } }
    }

    // ── values ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    fun encodePlaylistName(name: String) = "{\"n\":${quote(name.take(120))}}"

    fun decodePlaylistName(value: String): String? = field(value, "n")?.takeIf { it.isNotBlank() }

    fun encodeSong(meta: SongMeta) = "{\"t\":${quote(meta.title.take(100))},\"a\":${quote(meta.artist.take(80))},\"c\":${quote(meta.cover.take(150))}}"

    fun decodeSong(value: String): SongMeta? {
        val t = field(value, "t") ?: return null
        return SongMeta(t, field(value, "a").orEmpty(), field(value, "c").orEmpty())
    }

    /** A pin's snapshot. Fields are cut so the register stays far under the room's 800-character limit. */
    fun encodePin(meta: PinMeta) =
        "{\"t\":${quote(meta.title.take(100))},\"s\":${quote(meta.subtitle.take(80))},\"c\":${quote(meta.cover.take(150))}," +
            "\"l\":${quote(if (meta.local) "1" else "")},\"x\":${quote(meta.extra.take(60))},\"at\":${quote(meta.at.toString())}}"

    fun decodePin(value: String): PinMeta? {
        val t = field(value, "t")?.takeIf { it.isNotBlank() } ?: return null
        return PinMeta(
            title = t,
            subtitle = field(value, "s").orEmpty(),
            cover = field(value, "c").orEmpty(),
            local = field(value, "l") == "1",
            extra = field(value, "x").orEmpty(),
            at = field(value, "at")?.toLongOrNull() ?: 0L,
        )
    }

    fun encodeAlbum(meta: AlbumMeta) =
        "{\"t\":${quote(meta.title.take(100))},\"c\":${quote(meta.cover.take(150))},\"p\":${quote(meta.playlistId.take(60))}," +
            "\"y\":${quote(meta.year?.toString().orEmpty())},\"n\":${quote(meta.songCount.toString())},\"d\":${quote(meta.duration.toString())}}"

    fun decodeAlbum(value: String): AlbumMeta? {
        val t = field(value, "t") ?: return null
        return AlbumMeta(
            title = t,
            cover = field(value, "c").orEmpty(),
            playlistId = field(value, "p").orEmpty(),
            year = field(value, "y")?.toIntOrNull(),
            songCount = field(value, "n")?.toIntOrNull() ?: 0,
            duration = field(value, "d")?.toIntOrNull() ?: 0,
        )
    }

    /** JSON string literal with the escapes the Worker and Android's parser both accept. */
    internal fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append(' ')
            else -> append(c)
        }
        append('"')
    }

    /** Reads one string field of the tiny flat objects above (no nesting, only the escapes [quote] writes). */
    internal fun field(json: String, name: String): String? {
        val marker = "\"$name\":\""
        val start = json.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
        val out = StringBuilder()
        var i = start
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> return out.toString()
                c == '\\' && i + 1 < json.length -> {
                    when (val n = json[i + 1]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        else -> out.append(n)
                    }
                    i++
                }
                else -> out.append(c)
            }
            i++
        }
        return null
    }

    // ── safety ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** A batch may remove at most this many things, or this fraction of what the ledger knows — whichever is larger. */
    const val BRAKE_MIN = 25
    const val BRAKE_FRACTION = 0.30

    /**
     * True when applying [removals] removals would look like a mass deletion (a bug, an emptied account, a restored backup on the
     * other device) rather than a person tidying up: more than [BRAKE_MIN] AND more than [BRAKE_FRACTION] of the [known] items.
     * The batch is then held and the user is told; nothing is deleted on a hunch.
     */
    fun brakeTrips(removals: Int, known: Int): Boolean = removals > BRAKE_MIN && removals > known * BRAKE_FRACTION
}
