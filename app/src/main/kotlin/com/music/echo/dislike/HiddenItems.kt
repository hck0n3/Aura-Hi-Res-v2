package iad1tya.echo.music.dislike

/**
 * Registry row 811 (owner H6a, 2026-11-24): the «Ocultos» screen lists every set of [DislikeStore] — what the user hid
 * with «No me gusta», «No recomendar» and «Menos de esto» — and undoes one entry at a time. Pure: the screen feeds it the
 * store's snapshot and asks it which store call reverses a row, so the mapping is testable without a device.
 */
object HiddenItems {
    enum class Kind { SONG, ARTIST, ALBUM, PLAYLIST, SOFT_SONG, SOFT_ARTIST }

    data class Row(val kind: Kind, val id: String)

    /** Every hidden entry, grouped by kind in a fixed order, each group sorted so the list does not jump between emissions. */
    fun rows(d: DislikeStore.Disliked): List<Row> =
        listOf(
            Kind.SONG to d.songs,
            Kind.ARTIST to d.artists,
            Kind.ALBUM to d.albums,
            Kind.PLAYLIST to d.playlists,
            Kind.SOFT_SONG to d.softSongs,
            Kind.SOFT_ARTIST to d.softArtists,
        ).flatMap { (kind, ids) -> ids.sorted().map { Row(kind, it) } }

    /** True when the row is a song: its undo also clears the thumbs-down on the account (as the player's own undo does). */
    fun clearsAccountDislike(row: Row): Boolean = row.kind == Kind.SONG

    /** The one [DislikeStore] edit that reverses [row]; the others are left alone. */
    suspend fun undo(store: DislikeStore, row: Row) {
        when (row.kind) {
            Kind.SONG -> store.undislikeSong(row.id)
            Kind.ARTIST -> store.undislikeArtist(row.id)
            Kind.ALBUM -> store.undislikeAlbum(row.id)
            Kind.PLAYLIST -> store.undislikePlaylist(row.id)
            Kind.SOFT_SONG -> store.undoSoftDislikeSong(row.id)
            Kind.SOFT_ARTIST -> store.undoSoftDislikeArtist(row.id)
        }
    }
}
