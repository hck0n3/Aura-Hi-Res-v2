package iad1tya.echo.music.localmedia

import java.util.Locale
import kotlin.math.abs

/**
 * Row 882: what says that a local song the scan no longer sees and a file it sees for the first time are the same
 * recording, moved or renamed (a file manager, a new SD card path, MediaStore rebuilding its ids): the same kind (audio
 * or video), the same byte size, the same title and album tags and the same duration to the second.
 */
internal data class LocalSongIdentity(
    val id: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val durationSeconds: Int,
    val title: String,
    val albumName: String?,
)

/**
 * [moves]: old id -> new id, each pair the ONLY candidate of the other. [ambiguous]: old ids with candidates but no single
 * one (two copies of the same file, two identical rips): never merged with anything.
 */
internal data class LocalSongMoves(
    val moves: Map<String, String>,
    val ambiguous: Set<String>,
)

/** Durations are stored in whole seconds (milliseconds / 1000), so the same file can land one second apart. */
private const val DurationToleranceSeconds = 1

private fun LocalSongIdentity.tagKey(): Triple<Boolean, Long, Pair<String, String>>? {
    if (sizeBytes <= 0L) return null
    val normalizedTitle = title.trim().lowercase(Locale.ROOT)
    if (normalizedTitle.isEmpty()) return null
    return Triple(isVideo, sizeBytes, normalizedTitle to albumName?.trim()?.lowercase(Locale.ROOT).orEmpty())
}

/** Pure. A pair is only a move when the match is one-to-one in both directions; everything else stays apart. */
internal fun matchMovedLocalSongs(
    missing: List<LocalSongIdentity>,
    appeared: List<LocalSongIdentity>,
): LocalSongMoves {
    val appearedByKey = appeared.mapNotNull { song -> song.tagKey()?.let { it to song } }
        .groupBy({ it.first }, { it.second })
    val candidatesOfMissing = missing.associate { old ->
        old.id to old.tagKey()?.let { key ->
            appearedByKey[key].orEmpty().filter { abs(it.durationSeconds - old.durationSeconds) <= DurationToleranceSeconds }
        }.orEmpty()
    }
    val claimsOnAppeared = candidatesOfMissing.values.flatten().groupingBy(LocalSongIdentity::id).eachCount()
    val moves = LinkedHashMap<String, String>()
    val ambiguous = LinkedHashSet<String>()
    candidatesOfMissing.forEach { (oldId, candidates) ->
        when {
            candidates.isEmpty() -> Unit
            candidates.size == 1 && claimsOnAppeared[candidates.single().id] == 1 -> moves[oldId] = candidates.single().id
            else -> ambiguous += oldId
        }
    }
    return LocalSongMoves(moves = moves, ambiguous = ambiguous)
}
