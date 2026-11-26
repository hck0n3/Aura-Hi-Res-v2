package iad1tya.echo.music.localmedia

import java.time.LocalDateTime
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Row 881: incremental local scan. Pure Kotlin, so what decides "this file has not changed since the last scan" is
 * unit-tested on the JVM.
 *
 * Bump [LocalScanRulesVersion] whenever the scan would build a DIFFERENT track from the same unchanged file (tag
 * reading, artist splitting, album identity): every remembered build is then discarded once and each file is read
 * again. A new or removed field of the remembered track is detected without a bump (its serial shape changes).
 */
internal const val LocalScanRulesVersion = 1

/** What MediaStore says about a file: size, DATE_MODIFIED (seconds) and, on Android 11+, GENERATION_MODIFIED. */
@Serializable
internal data class LocalFileFingerprint(
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val generation: Long? = null,
)

/** The conditions under which every remembered build is still valid. */
@Serializable
internal data class LocalScanCacheHeader(
    val rules: Int,
    /** Serial shape of the remembered track: a field added or removed by a later version invalidates it. */
    val shape: String,
    /** MediaStore's opaque version (Android 10+): it changes when MediaStore rebuilds its database and reuses ids. */
    val mediaStoreVersion: String?,
)

/**
 * The remembered builds may be reused: not a full rescan, built by these rules into this shape from this MediaStore.
 * On Android 10+ an unreadable MediaStore version reuses nothing ([versionRequired]).
 */
internal fun localScanCacheUsable(
    stored: LocalScanCacheHeader?,
    current: LocalScanCacheHeader,
    fullRescan: Boolean,
    versionRequired: Boolean,
): Boolean = !fullRescan && stored != null && stored == current && (!versionRequired || current.mediaStoreVersion != null)

/** A file is unchanged when MediaStore reports exactly what it reported at the last scan (an unknown size never is). */
internal fun isUnchangedFile(remembered: LocalFileFingerprint?, current: LocalFileFingerprint): Boolean =
    remembered != null && current.sizeBytes > 0L && remembered == current

/**
 * Android 11+: MediaStore's version and generation of every volume, plus the scan settings. When an automatic scan
 * (app start, MediaStore change) finds the same stamp as the last successful scan, nothing it reads can have changed.
 * Null when any part is unknown: the automatic scan then runs.
 */
internal fun localAutoScanStamp(
    volumeStates: Map<String, String>?,
    scanConfig: LocalSongScanConfig,
    extra: Collection<String> = emptyList(),
): String? {
    if (volumeStates.isNullOrEmpty()) return null
    val volumes = volumeStates.toSortedMap().entries.joinToString(";") { (volume, state) -> "$volume=$state" }
    fun folders(entries: Set<String>) = entries.map { it.lowercase(Locale.ROOT) }.sorted().joinToString("|")
    return listOf(
        "r$LocalScanRulesVersion",
        volumes,
        "min=${scanConfig.sanitizedMinimumDurationSeconds}",
        "in=${folders(scanConfig.sanitizedIncludedFolders)}",
        "ex=${folders(scanConfig.sanitizedExcludedFolders)}",
        "x=${extra.sorted().joinToString("|")}",
    ).joinToString("\n")
}

internal fun autoScanFindsNothingNew(storedStamp: String?, currentStamp: String?): Boolean =
    storedStamp != null && currentStamp != null && storedStamp == currentStamp

/** The serial shape of a remembered build: names and types of every field, nested ones included. */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.shape(depth: Int = 0): String =
    if (elementsCount == 0 || depth > 3) {
        serialName
    } else {
        (0 until elementsCount).joinToString(",", "$serialName(", ")") { index ->
            "${getElementName(index)}:${getElementDescriptor(index).shape(depth + 1)}"
        }
    }

/** A remembered track's modification date, as ISO-8601 text. */
internal object IsoLocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalScanLocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
