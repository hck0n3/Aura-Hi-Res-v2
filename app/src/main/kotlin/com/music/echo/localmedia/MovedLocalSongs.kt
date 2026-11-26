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
 * one (two copies of the same file, two identical rips), or whose only match cannot take its data: never merged.
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

private fun candidatesOfMissing(
    missing: List<LocalSongIdentity>,
    appeared: List<LocalSongIdentity>,
): Map<String, List<LocalSongIdentity>> {
    val appearedByKey = appeared.mapNotNull { song -> song.tagKey()?.let { it to song } }
        .groupBy({ it.first }, { it.second })
    return missing.associate { old ->
        old.id to old.tagKey()?.let { key ->
            appearedByKey[key].orEmpty().filter { abs(it.durationSeconds - old.durationSeconds) <= DurationToleranceSeconds }
        }.orEmpty()
    }
}

/** The files of [appeared] that match some missing song: the only ones whose rows the scan needs to look at. */
internal fun movedSongCandidateIds(missing: List<LocalSongIdentity>, appeared: List<LocalSongIdentity>): Set<String> =
    candidatesOfMissing(missing, appeared).values.flatten().mapTo(LinkedHashSet(), LocalSongIdentity::id)

/**
 * Pure. [appeared] is every file the scan sees, new or already in the library; only those [canReceive] can take a missing
 * song's data. A pair is only a move when the match among those is one-to-one in both directions. Every other missing
 * song with a match ANYWHERE in [appeared] is [LocalSongMoves.ambiguous]: so it stays ambiguous (kept until the user
 * confirms «Quitar») on the next scans too, after its candidates got rows of their own (row 882, review repair).
 */
internal fun matchMovedLocalSongs(
    missing: List<LocalSongIdentity>,
    appeared: List<LocalSongIdentity>,
    canReceive: (String) -> Boolean = { true },
): LocalSongMoves {
    val candidates = candidatesOfMissing(missing, appeared)
    val receivers = candidates.mapValues { (_, matches) -> matches.filter { canReceive(it.id) } }
    val claimsOnAppeared = receivers.values.flatten().groupingBy(LocalSongIdentity::id).eachCount()
    val moves = LinkedHashMap<String, String>()
    val ambiguous = LinkedHashSet<String>()
    candidates.forEach { (oldId, matches) ->
        val receiving = receivers.getValue(oldId)
        when {
            matches.isEmpty() -> Unit
            receiving.size == 1 && claimsOnAppeared[receiving.single().id] == 1 -> moves[oldId] = receiving.single().id
            else -> ambiguous += oldId
        }
    }
    return LocalSongMoves(moves = moves, ambiguous = ambiguous)
}
