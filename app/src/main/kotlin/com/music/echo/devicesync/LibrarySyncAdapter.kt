package iad1tya.echo.music.devicesync

import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.devicesync.LibrarySyncCore.ON
import iad1tya.echo.music.devicesync.LibrarySyncCore.Plan
import iad1tya.echo.music.dislike.DislikeStore
import iad1tya.echo.music.models.MediaMetadata
import java.time.LocalDateTime
import timber.log.Timber

/**
 * Row 536 — the only place the library sync touches the database and the dislike store: it READS what this device has as a flat
 * map of facts ([readLocal]) and WRITES what other devices changed ([apply]). Everything is plain SQL through the DAO, never the
 * paths that push to the YouTube account, so a change that came from another device is not sent back out.
 */
class LibrarySyncAdapter(
    private val database: MusicDatabase,
    private val dislikes: DislikeStore,
): LibraryBackend {
    /**
     * The present facts: key → value ("1" / the playlist's name as JSON). [songMetaKnown] says whether a song's `sg:` register is
     * already in the ledger, so metadata is read only for songs the others have never been told about.
     */
    override suspend fun readLocal(songMetaKnown: (String) -> Boolean): Map<String, String> {
        val facts = LinkedHashMap<String, String>()
        val referenced = LinkedHashSet<String>()

        for (id in database.likedSongIdsSyncable()) {
            if (!LibrarySyncCore.syncableId(id)) continue
            facts[LibrarySyncCore.likeKey(id)] = ON
            referenced += id
        }

        val keyOf = HashMap<String, String>()
        for (row in database.syncPlaylistRows()) {
            val key = row.browseId ?: row.id
            if (!LibrarySyncCore.syncableId(key)) continue
            keyOf[row.id] = key
            facts[LibrarySyncCore.playlistKey(key)] = LibrarySyncCore.encodePlaylistName(row.name)
        }
        for (row in database.syncMemberRows()) {
            val key = keyOf[row.playlistId] ?: continue
            if (!LibrarySyncCore.syncableId(row.songId)) continue
            facts[LibrarySyncCore.memberKey(key, row.songId)] = ON
            referenced += row.songId
        }

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

        // 1. Songs another device told us about and we do not have: create them, so a like or a playlist entry has something to show.
        for ((id, meta) in plan.songs) {
            if (database.syncSongExists(id) > 0) continue
            val artists = if (meta.artist.isBlank()) emptyList() else listOf(MediaMetadata.Artist(id = null, name = meta.artist))
            database.insert(
                MediaMetadata(id = id, title = meta.title, artists = artists, duration = -1, thumbnailUrl = meta.cover.ifBlank { null }),
            )
            applied++
        }

        // 2. Playlists: create or rename.
        for ((key, name) in plan.playlistUpserts) {
            val local = database.syncPlaylistIdFor(key)
            if (local == null) {
                val ownId = isOwnPlaylistId(key)
                database.insert(
                    PlaylistEntity(
                        id = if (ownId) key else PlaylistEntity.generatePlaylistId(),
                        name = name,
                        browseId = if (ownId) null else key,
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true,
                    ),
                )
            } else {
                database.syncRenamePlaylist(local, name, LocalDateTime.now())
            }
            applied++
        }

        // 3. Songs added to playlists.
        for ((key, songId) in plan.memberAdds) {
            val playlist = database.syncPlaylistIdFor(key)
            if (playlist == null || database.syncSongExists(songId) == 0) {
                unresolved++
                continue
            }
            if (database.syncMemberCount(playlist, songId) == 0) {
                database.insert(PlaylistSongMap(playlistId = playlist, songId = songId, position = database.syncMaxPosition(playlist) + 1))
                applied++
            }
        }

        // 4. Likes.
        val now = LocalDateTime.now()
        val changedLikes = ArrayList<Pair<String, Boolean>>()
        for ((id, liked) in plan.likes) {
            if (liked && database.syncSongExists(id) == 0) {
                unresolved++
                continue
            }
            if (database.syncIsLiked(id) != liked) changedLikes += id to liked
            database.syncSetLiked(id, liked, if (liked) now else null)
            applied++
        }
        mirrorLikesToAccount(changedLikes)

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
            val playlist = database.syncPlaylistIdFor(key) ?: continue
            database.syncRemoveMember(playlist, songId)
            applied++
        }
        for (key in plan.playlistRemovals) {
            val playlist = database.syncPlaylistIdFor(key) ?: continue
            database.syncDeletePlaylist(playlist)
            applied++
        }

        if (unresolved > 0) Timber.tag("DeviceSync").w("LIB_SYNC apply unresolved=%d", unresolved)
        return ApplyResult(applied, unresolved)
    }

    /**
     * Owner 2026-10-30: «todo lo que me gusta que aparezca de inmediato también en YouTube». The device where the like was made
     * already sent it; this covers the case where THAT device could not (signed out of YouTube, no network at that moment) and this
     * one can. YouTube ignores a like that is already there. A first sync that brings thousands of likes is NOT replayed against the
     * account one by one (that is the YouTube sync's job): only the few changes a person makes live.
     */
    private fun mirrorLikesToAccount(changed: List<Pair<String, Boolean>>) {
        if (changed.isEmpty() || changed.size > MIRROR_MAX) return
        for ((id, liked) in changed) {
            iad1tya.echo.music.utils.AccountPush.submit(
                if (liked) iad1tya.echo.music.utils.AccountPushPolicy.Op.SONG_LIKE else iad1tya.echo.music.utils.AccountPushPolicy.Op.SONG_UNLIKE,
                id,
            )
        }
        Timber.tag("DeviceSync").i("LIB_SYNC likes mirrored to account=%d", changed.size)
    }

    /** `LP` + 8 letters is how this app names a playlist that lives only on the device; anything else is a YouTube browseId. */
    private fun isOwnPlaylistId(key: String) = key.length == 10 && key.startsWith("LP") && key.drop(2).all { it.isLetter() }

    private companion object {
        const val MIRROR_MAX = 25
    }
}
