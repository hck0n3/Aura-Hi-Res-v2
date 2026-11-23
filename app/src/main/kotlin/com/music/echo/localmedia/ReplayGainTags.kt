package iad1tya.echo.music.localmedia

/**
 * ReplayGain tags of local files, turned into the loudness value the player already levels by (row 724).
 * Pure Kotlin (no Android), so the parsing and the dB mapping are unit-tested on the JVM.
 */

/** ReplayGain 2.0 reference loudness: a track gain brings the track to -18 LUFS. */
const val REPLAYGAIN_REFERENCE_LUFS: Double = -18.0

/** Reference of `FormatEntity.loudnessDb` (YouTube's value): dB above -14 LUFS, normalized by applying `-loudnessDb`. */
const val LOUDNESS_DB_REFERENCE_LUFS: Double = -14.0

/** A gain outside this window is a broken tag, not a real ReplayGain value. */
private const val MAX_PLAUSIBLE_REPLAYGAIN_DB: Double = 60.0

private val LeadingNumber = Regex("""^[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)""")

/** Parses a ReplayGain tag value such as `-6.54 dB`, `+1.20 dB` or `-6,54`. Null when it is not a usable gain. */
fun parseReplayGainDb(raw: String?): Double? {
    val match = LeadingNumber.find(raw?.trim() ?: return null) ?: return null
    val value = match.value.replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && kotlin.math.abs(it) <= MAX_PLAUSIBLE_REPLAYGAIN_DB }
}

/**
 * The ReplayGain of a file from its tag maps (container tags and per-stream tags): the TRACK gain, else the ALBUM gain.
 * Keys are matched ignoring case and any prefix, so the Vorbis comment `REPLAYGAIN_TRACK_GAIN`, the ID3 `TXXX` of the
 * same name and the iTunes `----:com.apple.iTunes:replaygain_track_gain` atom all count.
 */
fun replayGainFromTags(tagMaps: List<Map<String, String>>): Double? =
    gainForKey(tagMaps, "replaygain_track_gain") ?: gainForKey(tagMaps, "replaygain_album_gain")

private fun gainForKey(tagMaps: List<Map<String, String>>, key: String): Double? =
    tagMaps.asSequence()
        .flatMap { it.entries.asSequence() }
        .filter { (name, _) -> name.trim().endsWith(key, ignoreCase = true) }
        .firstNotNullOfOrNull { (_, value) -> parseReplayGainDb(value) }

/**
 * ReplayGain gain (dB) → `FormatEntity.loudnessDb`. The tag says the track's integrated loudness is
 * `-18 - gain` LUFS; `loudnessDb` is that loudness minus -14 LUFS, so `loudnessDb = -gain - 4`.
 * Example: a master at -8 LUFS carries gain -10 dB → loudnessDb +6 → the player applies -6 dB → -14 LUFS.
 */
fun replayGainToLoudnessDb(gainDb: Double): Double =
    REPLAYGAIN_REFERENCE_LUFS - gainDb - LOUDNESS_DB_REFERENCE_LUFS

/** What the scan knows about one file: its size and modification time when its tags were read, and what they said. */
data class ReplayGainCacheEntry(
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    /** Null: the file was read and carries no ReplayGain. */
    val loudnessDb: Double?,
)

/** One line per file: `id \t size \t mtime \t loudnessDb` (empty loudness = read, no tag). Broken lines are skipped. */
fun parseReplayGainCache(lines: List<String>): Map<String, ReplayGainCacheEntry> =
    lines.mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4 || parts[0].isEmpty()) return@mapNotNull null
        val size = parts[1].toLongOrNull() ?: return@mapNotNull null
        val modified = parts[2].toLongOrNull() ?: return@mapNotNull null
        val loudness = if (parts[3].isEmpty()) null else parts[3].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@mapNotNull null
        parts[0] to ReplayGainCacheEntry(size, modified, loudness)
    }.toMap()

fun serializeReplayGainCache(entries: Map<String, ReplayGainCacheEntry>): String =
    entries.entries
        .filterNot { (id, _) -> id.isEmpty() || id.contains('\t') || id.contains('\n') }
        .joinToString("\n") { (id, e) -> "$id\t${e.sizeBytes}\t${e.modifiedEpochSeconds}\t${e.loudnessDb ?: ""}" }

/** The cached loudness of [id], only while the file still has the size and modification time it had when read. */
fun cachedReplayGainLoudness(
    cache: Map<String, ReplayGainCacheEntry>,
    id: String,
    sizeBytes: Long,
    modifiedEpochSeconds: Long,
): Double? = cache[id]?.takeIf { it.sizeBytes == sizeBytes && it.modifiedEpochSeconds == modifiedEpochSeconds }?.loudnessDb

/**
 * Which files this scan reads the tags of: those never read, or changed (size or modification time) since they were,
 * at most [maxReads] of them. The rest wait for the next scan and keep today's behaviour meanwhile.
 */
fun replayGainReadsNeeded(
    files: List<Triple<String, Long, Long>>,
    cache: Map<String, ReplayGainCacheEntry>,
    maxReads: Int,
): List<String> =
    files.asSequence()
        .filter { (id, size, modified) -> cache[id]?.let { it.sizeBytes != size || it.modifiedEpochSeconds != modified } ?: true }
        .map { it.first }
        .take(maxReads.coerceAtLeast(0))
        .toList()
