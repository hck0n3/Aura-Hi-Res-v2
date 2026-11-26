package iad1tya.echo.music.localmedia

import java.util.Locale

/**
 * Row 879 (plan, phase 4: «orden de disco/pista, artista del álbum»). Pure Kotlin, so what decides the order of an
 * album's songs and which songs make one album is unit-tested on the JVM.
 */

/**
 * The leading number of a disc or track tag: «3», «03», «3/12», « 2 of 2». Null when there is none or it is 0 (an unset
 * tag), so a file without the tag sorts after the numbered ones instead of first.
 */
internal fun parseTagNumber(raw: String?): Int? {
    val digits = raw?.trim()?.takeWhile(Char::isDigit)?.takeIf(String::isNotEmpty) ?: return null
    return digits.take(6).toIntOrNull()?.takeIf { it > 0 }
}

/** Disc and track of one file; null when the file does not say. */
internal data class LocalTrackPosition(val disc: Int?, val track: Int?)

/**
 * The tags win ([cdTrackNumber], [discNumber]: MediaStore on Android 11+, or the file itself). Otherwise MediaStore's
 * TRACK column, which holds both on every version: 1xxx is disc 1, 2xxx disc 2, and below 1000 the track alone.
 */
internal fun localTrackPosition(cdTrackNumber: String?, discNumber: String?, encodedTrack: Int?): LocalTrackPosition {
    val encoded = encodedTrack?.takeIf { it > 0 }
    val encodedDisc = encoded?.takeIf { it >= 1000 }?.let { it / 1000 }
    val encodedNumber = encoded?.let { it % 1000 }?.takeIf { it > 0 }
    return LocalTrackPosition(
        disc = parseTagNumber(discNumber) ?: encodedDisc,
        track = parseTagNumber(cdTrackNumber) ?: encodedNumber,
    )
}

/** What the album order needs to know of one song. */
internal data class LocalAlbumOrderEntry(
    val id: String,
    val albumId: String?,
    val disc: Int?,
    val track: Int?,
    val title: String,
)

/**
 * Inside an album: disc (a file without a disc tag counts as disc 1), then track (untagged ones after the numbered
 * ones), then title, then id, so the order never depends on the order MediaStore listed the files in.
 */
internal val LocalAlbumOrder: Comparator<LocalAlbumOrderEntry> =
    compareBy<LocalAlbumOrderEntry> { it.disc ?: 1 }
        .thenBy { it.track ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase(Locale.ROOT) }
        .thenBy { it.id }

/** Song id -> its position (0, 1, 2…) inside its album, written to `song_album_map.index`, which the album lists sort by. */
internal fun localAlbumIndexes(entries: List<LocalAlbumOrderEntry>): Map<String, Int> {
    val indexes = HashMap<String, Int>()
    entries.filter { it.albumId != null }.groupBy { it.albumId }.values.forEach { album ->
        album.sortedWith(LocalAlbumOrder).forEachIndexed { index, entry -> indexes[entry.id] = index }
    }
    return indexes
}

/**
 * Which artist an album made without a MediaStore album id (a walked picked-folder file, a video) is grouped by: its
 * ALBUM ARTIST when the tag says, so a compilation stays one album; otherwise the first track artist, as before (the same
 * album id for the same untagged files).
 */
internal fun localAlbumGroupArtistId(albumArtistIds: List<String>, trackArtistIds: List<String>): String? =
    albumArtistIds.firstOrNull() ?: trackArtistIds.firstOrNull()

/**
 * Row 879: the artists an album shows. The album artists of its songs when any song has the tag; otherwise every track
 * artist, as before.
 */
internal fun localAlbumArtistIds(albumArtistIdsPerSong: List<List<String>>, trackArtistIdsPerSong: List<List<String>>): List<String> {
    val albumArtists = albumArtistIdsPerSong.flatten().distinct()
    return albumArtists.ifEmpty { trackArtistIdsPerSong.flatten().distinct() }
}

/**
 * Row 879, data safety: a song whose artists or album got another id (a new artist split, an album now grouped by its
 * album artist) must not take the follow or the bookmark of the old one with it when the old one is pruned. For each old
 * id that no scanned song uses any more, the first id of the first song that had it which is not [alreadyThere] (in the
 * library before this scan) inherits from it: an artist or album that already existed keeps only its own marks, so one
 * that was merely dropped from a tag never passes its follow to it. A new id inherits from one old id at most. Returns
 * new id -> old id.
 */
internal fun inheritedLocalIds(
    previousAndCurrent: List<Pair<List<String>, List<String>>>,
    stillUsed: Set<String>,
    alreadyThere: Set<String> = emptySet(),
): Map<String, String> {
    val inherited = LinkedHashMap<String, String>()
    val given = HashSet<String>()
    previousAndCurrent.forEach { (previous, current) ->
        val heir = current.firstOrNull { it !in alreadyThere } ?: return@forEach
        previous.forEach { old ->
            if (old !in stillUsed && old !in given && heir !in inherited) {
                inherited[heir] = old
                given += old
            }
        }
    }
    return inherited
}
