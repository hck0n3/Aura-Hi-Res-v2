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
 * The playlist key is the playlist's `browseId` when it has one (the same on every device), else its own id.
 */
object LibrarySyncCore {
    const val ON = "1"
    const val OFF = "0"

    /** One register as it travels and as the local ledger remembers it. [pending] = not yet acknowledged by the room. */
    data class Reg(val key: String, val ts: Long, val value: String, val pending: Boolean = false)

    enum class Kind { LIKE, DISLIKE, PLAYLIST, MEMBER, SONG }

    sealed interface Key {
        val kind: Kind
        data class Like(val songId: String) : Key { override val kind get() = Kind.LIKE }
        data class Dislike(val set: String, val id: String) : Key { override val kind get() = Kind.DISLIKE }
        data class Playlist(val playlistKey: String) : Key { override val kind get() = Kind.PLAYLIST }
        data class Member(val playlistKey: String, val songId: String) : Key { override val kind get() = Kind.MEMBER }
        data class Song(val songId: String) : Key { override val kind get() = Kind.SONG }
    }

    /** The six dislike sets of `DislikeStore`, by their short names in the key. */
    val DISLIKE_SETS = listOf("song", "artist", "album", "playlist", "softsong", "softartist")

    fun likeKey(songId: String) = "lk:$songId"
    fun dislikeKey(set: String, id: String) = "dk:$set:$id"
    fun playlistKey(playlistKey: String) = "pl:$playlistKey"
    fun memberKey(playlistKey: String, songId: String) = "pm:$playlistKey:$songId"
    fun songKey(songId: String) = "sg:$songId"

    /** True when an id can be part of a key (non-empty, no colon, not absurdly long). */
    fun syncableId(id: String) = id.isNotEmpty() && ':' !in id && id.length <= 80

    fun parse(key: String): Key? {
        val colon = key.indexOf(':')
        if (colon < 0) return null
        val rest = key.substring(colon + 1)
        return when (key.substring(0, colon)) {
            "lk" -> rest.takeIf { it.isNotEmpty() }?.let(Key::Like)
            "sg" -> rest.takeIf { it.isNotEmpty() }?.let(Key::Song)
            "pl" -> rest.takeIf { it.isNotEmpty() && ':' !in it }?.let(Key::Playlist)
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
        for ((key, value) in current) {
            val known = ledger[key]
            if (key.startsWith("sg:")) {
                if (known == null) ops += Reg(key, now, value, pending = true)
                continue
            }
            if (known == null || known.value != value) ops += Reg(key, maxOf(now, (known?.ts ?: 0L) + 1), value, pending = true)
        }
        for ((key, known) in ledger) {
            if (key.startsWith("sg:") || known.value == OFF || key in current) continue
            ops += Reg(key, maxOf(now, known.ts + 1), OFF, pending = true)
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
    ) {
        val removalCount: Int
            get() = playlistRemovals.size + memberRemovals.size + likes.count { !it.second } + dislikes.count { !it.third }
    }

    data class SongMeta(val title: String, val artist: String, val cover: String)

    fun plan(accepted: List<Reg>): Plan {
        val songs = ArrayList<Pair<String, SongMeta>>()
        val upserts = ArrayList<Pair<String, String>>()
        val plRemovals = ArrayList<String>()
        val adds = ArrayList<Pair<String, String>>()
        val memberRemovals = ArrayList<Pair<String, String>>()
        val likes = ArrayList<Pair<String, Boolean>>()
        val dislikes = ArrayList<Triple<String, String, Boolean>>()
        for (reg in accepted) {
            when (val k = parse(reg.key)) {
                is Key.Song -> decodeSong(reg.value)?.let { songs += k.songId to it }
                is Key.Playlist ->
                    if (reg.value == OFF) plRemovals += k.playlistKey else decodePlaylistName(reg.value)?.let { upserts += k.playlistKey to it }
                is Key.Member -> if (reg.value == ON) adds += k.playlistKey to k.songId else if (reg.value == OFF) memberRemovals += k.playlistKey to k.songId
                is Key.Like -> if (reg.value == ON || reg.value == OFF) likes += k.songId to (reg.value == ON)
                is Key.Dislike -> if (reg.value == ON || reg.value == OFF) dislikes += Triple(k.set, k.id, reg.value == ON)
                null -> Unit
            }
        }
        return Plan(songs, upserts, plRemovals, adds, memberRemovals, likes, dislikes)
    }

    // ── values ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    fun encodePlaylistName(name: String) = "{\"n\":${quote(name.take(120))}}"

    fun decodePlaylistName(value: String): String? = field(value, "n")?.takeIf { it.isNotBlank() }

    fun encodeSong(meta: SongMeta) = "{\"t\":${quote(meta.title.take(100))},\"a\":${quote(meta.artist.take(80))},\"c\":${quote(meta.cover.take(150))}}"

    fun decodeSong(value: String): SongMeta? {
        val t = field(value, "t") ?: return null
        return SongMeta(t, field(value, "a").orEmpty(), field(value, "c").orEmpty())
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
