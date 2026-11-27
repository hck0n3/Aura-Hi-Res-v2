package iad1tya.echo.music.devicesync

import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.devicesync.LibrarySyncCore.ON
import iad1tya.echo.music.devicesync.LibrarySyncCore.Plan
import iad1tya.echo.music.dislike.DislikeStore
import iad1tya.echo.music.models.MediaMetadata
import java.time.LocalDateTime
import java.time.ZoneOffset
import timber.log.Timber

/**
 * Row 536 — the only place the library sync touches the database and the dislike store: it READS what this device has as a flat
 * map of facts ([readLocal]) and WRITES what other devices changed ([apply]). Everything is plain SQL through the DAO, never the
 * paths that push to the YouTube account, so a change that came from another device is not sent back out.
 */
class LibrarySyncAdapter(
    private val database: MusicDatabase,
    private val dislikes: DislikeStore,
    /** Row 584: the two places things are pinned (Biblioteca ▸ Fijados, «Fijadas en inicio»); null = pins are not synced (tests). */
    private val pins: PinsBackend? = null,
): LibraryBackend {
    /**
     * The present facts: key → value ("1" / the playlist's name as JSON). [songMetaKnown] says whether a song's `sg:` register is
     * already in the ledger, so metadata is read only for songs the others have never been told about.
     */
    override suspend fun readLocal(playlists: LibrarySyncCore.PlaylistIds, songMetaKnown: (String) -> Boolean): Map<String, String> {
        val facts = LinkedHashMap<String, String>()
        val referenced = LinkedHashSet<String>()

        for (id in database.likedSongIdsSyncable()) {
            if (!LibrarySyncCore.syncableId(id)) continue
            facts[LibrarySyncCore.likeKey(id)] = ON
            referenced += id
        }

        // Row 740: a list keeps the key it first travelled under; being linked to YouTube adds a `pb:` fact instead of a new key.
        val keyOfRow = HashMap<String, String>()
        val keyOf = HashMap<String, String>()
        val playlistRows = database.syncPlaylistRows()
        // Row 741: when two rows here share a key, only one of them says the list's name and songs.
        val sources = playlists.sources(playlistRows.map { LibrarySyncCore.PlaylistRow(it.id, it.browseId) })
        for (row in playlistRows) {
            val ref = LibrarySyncCore.PlaylistRow(row.id, row.browseId)
            val key = playlists.keyOf(ref)
            keyOfRow[row.id] = key
            if (!LibrarySyncCore.syncableId(key)) continue
            playlists.linkFact(ref)?.let { (k, v) -> facts[k] = v }
            if (sources[row.id] != key) continue
            keyOf[row.id] = key
            facts[LibrarySyncCore.playlistKey(key)] = LibrarySyncCore.encodePlaylistName(row.name)
        }
        // Row 900: the rows come in each list's order, so each song's position travels too (`po:`, LibrarySyncCore.PlaylistOrder).
        val order = LinkedHashMap<String, MutableList<String>>()
        for (row in database.syncMemberRows()) {
            val key = keyOf[row.playlistId] ?: continue
            if (!LibrarySyncCore.syncableId(row.songId)) continue
            facts[LibrarySyncCore.memberKey(key, row.songId)] = ON
            order.getOrPut(key) { ArrayList() } += row.songId
            referenced += row.songId
        }
        for ((key, songs) in order) facts.putAll(LibrarySyncCore.PlaylistOrder.facts(key, songs, playlists.orderOf(key)))

        for (row in database.syncSavedAlbumRows()) {
            if (!LibrarySyncCore.syncableId(row.id)) continue
            facts[LibrarySyncCore.albumKey(row.id)] = ON
            val metaKey = LibrarySyncCore.albumMetaKey(row.id)
            if (!songMetaKnown(metaKey)) {
                facts[metaKey] = LibrarySyncCore.encodeAlbum(
                    LibrarySyncCore.AlbumMeta(row.title, row.thumbnailUrl.orEmpty(), row.playlistId.orEmpty(), row.year, row.songCount, row.duration),
                )
            }
        }

        // The History screen (owner 2026-10-31: «el historial no está sincronizado»): the most recent listens, one create-only register
        // each. The second is the wall-clock time read as UTC, so every device rebuilds exactly the same moment.
        for (row in database.syncRecentPlays(PLAY_SYNC_LIMIT)) {
            if (!LibrarySyncCore.syncableId(row.songId)) continue
            facts[LibrarySyncCore.playKey(row.songId, row.timestamp.toEpochSecond(ZoneOffset.UTC))] = ON
            referenced += row.songId
        }

        pins?.let { runCatching { it.readLocal(keyOfRow) }.onSuccess { facts.putAll(it) } }

        val d = dislikes.snapshot()
        fun add(set: String, ids: Set<String>) = ids.filter(LibrarySyncCore::syncableId).forEach { facts[LibrarySyncCore.dislikeKey(set, it)] = ON }
        add("song", d.songs)
        add("artist", d.artists)
        add("album", d.albums)
        add("playlist", d.playlists)
        add("softsong", d.softSongs)
        add("softartist", d.softArtists)

        val unknown = referenced.filter { !songMetaKnown(LibrarySyncCore.songKey(it)) }
        for (chunk in unknown.chunked(400)) {
            for (row in database.syncSongRows(chunk)) {
                facts[LibrarySyncCore.songKey(row.id)] =
                    LibrarySyncCore.encodeSong(LibrarySyncCore.SongMeta(row.title, row.artist.orEmpty(), row.thumbnailUrl.orEmpty()))
            }
        }
        return facts
    }

    override suspend fun apply(plan: Plan): ApplyResult {
        var applied = 0
        var unresolved = 0
        val unplaced = HashSet<String>()

        // 1. Songs another device told us about and we do not have: create them, so a like or a playlist entry has something to show.
        for ((id, meta) in plan.songs) {
            if (database.syncSongExists(id) > 0) continue
            val artists = if (meta.artist.isBlank()) emptyList() else listOf(MediaMetadata.Artist(id = null, name = meta.artist))
            database.insert(
                MediaMetadata(id = id, title = meta.title, artists = artists, duration = -1, thumbnailUrl = meta.cover.ifBlank { null }),
            )
            applied++
        }

        // 1b. Albums another device saved and we have never seen: create the shell (the album page loads its songs from YouTube).
        for ((id, meta) in plan.albums) {
            if (database.syncAlbumExists(id) > 0) continue
            database.insert(
                AlbumEntity(
                    id = id,
                    playlistId = meta.playlistId.ifBlank { null },
                    title = meta.title,
                    year = meta.year,
                    thumbnailUrl = meta.cover.ifBlank { null },
                    songCount = meta.songCount,
                    duration = meta.duration,
                ),
            )
            applied++
        }

        // 1c. Listens (History screen) another device heard: add the ones not here yet, with no listening time (it was not heard HERE,
        // so the statistics of time listened stay true).
        for ((songId, second) in plan.plays) {
            if (database.syncSongExists(songId) == 0) {
                unresolved++
                unplaced += LibrarySyncCore.playKey(songId, second)
                continue
            }
            val at = LocalDateTime.ofEpochSecond(second, 0, ZoneOffset.UTC)
            if (database.syncPlayCount(songId, at, at.plusSeconds(1)) > 0) continue
            database.insert(Event(songId = songId, timestamp = at, playTime = 0L))
            applied++
        }

        // 2. Playlists: create or rename. Row 740: a register reaches only the list it names ([LibrarySyncCore.PlaylistTargets]); a
        // row outside the synced ones (a list taken out of the library) is still found the old way, by id or browseId.
        val targets = LibrarySyncCore.PlaylistTargets(
            database.syncPlaylistRows().map { LibrarySyncCore.PlaylistRow(it.id, it.browseId) }, plan.before, plan.after,
        )
        suspend fun unsynced(key: String) = database.syncPlaylistIdFor(key)?.takeIf { it !in targets.ids }
        for ((key, name) in plan.playlistUpserts) {
            val rows = targets.rowsOf(key)
            if (rows.isNotEmpty()) {
                rows.forEach { database.syncRenamePlaylist(it, name, LocalDateTime.now()) }
            } else if (targets.forAddition(key) == null) {
                val local = unsynced(key)
                if (local == null) {
                    val ownId = LibrarySyncCore.isOwnPlaylistId(key)
                    val entity = PlaylistEntity(
                        id = if (ownId) key else PlaylistEntity.generatePlaylistId(),
                        name = name,
                        browseId = if (ownId) null else key,
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true,
                        // Row 835: a list another device made and never put on YouTube stays off this device's account too (the
                        // library upload skips `isLocal`); if that device links it later, step 2b links it here as well.
                        isLocal = ownId,
                    )
                    database.insert(entity)
                    targets.add(LibrarySyncCore.PlaylistRow(entity.id, entity.browseId))
                } else {
                    database.syncRenamePlaylist(local, name, LocalDateTime.now())
                }
            }
            applied++
        }
        // 2b. Row 740: a list another device linked to YouTube is linked here too (never a browseId another list here already has), so
        // this device's own YouTube sync finds it instead of downloading a second copy, and its upload does not create a second one.
        for ((row, browseId) in targets.unlinked()) {
            database.syncLinkPlaylist(row, browseId)
        }

        // 3. Songs added to playlists.
        for ((key, songId) in plan.memberAdds) {
            val playlist = targets.forAddition(key) ?: unsynced(key)
            if (playlist == null || database.syncSongExists(songId) == 0) {
                unresolved++
                unplaced += LibrarySyncCore.memberKey(key, songId)
                continue
            }
            if (database.syncMemberCount(playlist, songId) == 0) {
                database.insert(PlaylistSongMap(playlistId = playlist, songId = songId, position = database.syncMaxPosition(playlist) + 1))
                applied++
            }
        }

        // 4. Likes.
        val now = LocalDateTime.now()
        // Row 835: what arrives here is NOT sent to this device's YouTube account (it may be another account): the device where the
        // user tapped already sent it to its own.
        for ((id, liked) in plan.likes) {
            if (liked && database.syncSongExists(id) == 0) {
                unresolved++
                unplaced += LibrarySyncCore.likeKey(id)
                continue
            }
            database.syncSetLiked(id, liked, if (liked) now else null)
            applied++
        }

        // 4b. Saved albums.
        for ((id, saved) in plan.albumSaves) {
            if (saved && database.syncAlbumExists(id) == 0) {
                unresolved++
                unplaced += LibrarySyncCore.albumKey(id)
                continue
            }
            database.syncSetAlbumSaved(id, if (saved) now else null)
            applied++
        }

        // 5. Dislikes.
        for ((set, id, on) in plan.dislikes) {
            when (set) {
                "song" -> if (on) dislikes.dislikeSong(id) else dislikes.undislikeSong(id)
                "artist" -> if (on) dislikes.dislikeArtist(id) else dislikes.undislikeArtist(id)
                "album" -> if (on) dislikes.dislikeAlbum(id) else dislikes.undislikeAlbum(id)
                "playlist" -> if (on) dislikes.dislikePlaylist(id) else dislikes.undislikePlaylist(id)
                "softsong" -> if (on) dislikes.softDislikeSong(id) else dislikes.undoSoftDislikeSong(id)
                "softartist" -> if (on) dislikes.softDislikeArtist(id) else dislikes.undoSoftDislikeArtist(id)
                else -> continue
            }
            applied++
        }

        // 6. Removals last, so a song moved between lists is never momentarily in none.
        for ((key, songId) in plan.memberRemovals) {
            val rows = targets.forRemoval(key) + listOfNotNull(unsynced(key))
            if (rows.isEmpty()) continue
            rows.forEach { database.syncRemoveMember(it, songId) }
            applied++
        }
        for (key in plan.playlistRemovals) {
            val rows = targets.forRemoval(key) + listOfNotNull(unsynced(key))
            if (rows.isEmpty()) continue
            rows.forEach { database.syncDeletePlaylist(it) }
            applied++
        }

        // 6b. Row 900: the songs of each list in the order the positions give them (only the order changes, here and nowhere else:
        // nothing of it goes to this device's YouTube account). A song with no position yet stays after the one it follows here.
        for (key in plan.reorder) {
            val positions = plan.after.orderOf(key)
            if (positions.isEmpty()) continue
            for (row in targets.rowsOf(key)) {
                val entries = database.syncPlaylistMapRows(row)
                val rank = LibrarySyncCore.PlaylistOrder.sorted(entries.map { it.songId }, positions).withIndex().associate { it.value to it.index }
                val wanted = entries.sortedWith(compareBy<PlaylistSongMap>({ rank[it.songId] ?: Int.MAX_VALUE }, { it.position }, { it.id }))
                var moved = false
                wanted.forEachIndexed { index, entry ->
                    if (entry.position != index) {
                        database.syncSetMapPosition(entry.id, index)
                        moved = true
                    }
                }
                if (moved) applied++
            }
        }

        // 7. Pins (Biblioteca ▸ Fijados and «Fijadas en inicio»): after the playlists, so a pinned playlist already exists here.
        val pinsBackend = pins
        if (pinsBackend != null && (plan.pinUpserts.isNotEmpty() || plan.pinRemovals.isNotEmpty())) {
            // The row here of every playlist a pin names, by the same rules as the playlist registers (row 740).
            val pinAdds = HashMap<String, String?>()
            for ((ref, _) in plan.pinUpserts) if (ref.code == "P") pinAdds[ref.id] = targets.forAddition(ref.id) ?: unsynced(ref.id)
            val pinRemovals = HashMap<String, List<String>>()
            for (ref in plan.pinRemovals) if (ref.code == "P") pinRemovals[ref.id] = targets.forRemoval(ref.id) + listOfNotNull(unsynced(ref.id))
            val result = runCatching { pinsBackend.applyPins(plan.pinUpserts, plan.pinRemovals, pinAdds, pinRemovals) }.getOrNull()
            if (result != null) {
                applied += result.applied
                unresolved += result.unresolved
                unplaced += result.unplaced
            } else {
                unplaced += plan.pinUpserts.map { LibrarySyncCore.pinKey(it.first) }
            }
        }

        if (unresolved > 0) Timber.tag("DeviceSync").w("LIB_SYNC apply unresolved=%d", unresolved)
        return ApplyResult(applied, unresolved, unplaced)
    }

    private companion object {
        /** How many of the latest listens are offered to the other devices; older ones already went out when they were new. */
        const val PLAY_SYNC_LIMIT = 300
    }
}

/** Row 584 — what the library sync needs from the two pin stores; the Android side is [LibraryPinsBackend]. */
interface PinsBackend {
    /** Every pin here as a register fact: `pn:…` key → [LibrarySyncCore.encodePin]. [keyOfRow]: playlist row id → its sync key (row 740). */
    suspend fun readLocal(keyOfRow: Map<String, String>): Map<String, String>

    /**
     * Writes what other devices pinned and unpinned. Returns how many it applied and how many it could not place yet. For playlist
     * pins, [rowForAddition] / [rowsForRemoval] give the row here that a sync key reaches (row 740).
     */
    suspend fun applyPins(
        upserts: List<Pair<LibrarySyncCore.PinRef, LibrarySyncCore.PinMeta>>,
        removals: List<LibrarySyncCore.PinRef>,
        rowForAddition: Map<String, String?>,
        rowsForRemoval: Map<String, List<String>>,
    ): ApplyResult
}
