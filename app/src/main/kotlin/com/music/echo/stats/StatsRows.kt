package iad1tya.echo.music.stats

/*
 * Registry row 909 — the plain rows the statistics read from Room (`DatabaseDao.stats…` queries, SELECT only, no schema
 * change). `timestamp` stays the raw stored value; [ListeningStats.fromStored] turns it into the local wall-clock time.
 */

data class StatsPlayRow(val songId: String, val timestamp: Long, val playTime: Long)

data class StatsSongRow(
    val id: String,
    val title: String,
    val duration: Int,
    val albumId: String?,
    val albumName: String?,
    val thumbnailUrl: String?,
)

data class StatsSongArtistRow(val songId: String, val artistId: String, val name: String, val position: Int)

data class StatsArtistFirstPlayRow(val artistId: String, val name: String, val firstPlayed: Long)

/** The plays and songs of a range, as [ListeningStats.summarize] takes them. */
fun statsInput(
    plays: List<StatsPlayRow>,
    songs: List<StatsSongRow>,
    artists: List<StatsSongArtistRow>,
): Pair<List<ListeningStats.Play>, Map<String, ListeningStats.SongInfo>> {
    val artistsBySong = artists.groupBy { it.songId }
    val infos = songs.associate { s ->
        s.id to ListeningStats.SongInfo(
            id = s.id,
            title = s.title,
            artists = artistsBySong[s.id].orEmpty().sortedBy { it.position }.map { ListeningStats.ArtistInfo(it.artistId, it.name) },
            albumId = s.albumId,
            albumTitle = s.albumName,
            durationSeconds = s.duration,
            thumbnailUrl = s.thumbnailUrl,
        )
    }
    return plays.map { ListeningStats.Play(it.songId, ListeningStats.fromStored(it.timestamp), it.playTime) } to infos
}
