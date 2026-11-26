package iad1tya.echo.music.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * `.lrc` files next to a song on this phone (registry row 884): `Song.flac` → `Song.lrc` / `Song.LRC`.
 * Pure Kotlin (no Android), so the naming, the decoding and the clean-up are unit-tested on the JVM.
 * The reader that finds the file is [LocalLrcReader].
 */
object LocalLrcSidecar {
    /** Provider name stored with the lyrics row and shown as «Letra de LRC». Never an online provider. */
    const val PROVIDER = "LRC"

    /** A lyrics file is a few KB; anything above this is not one (design limit, not measured). */
    const val MAX_BYTES = 512 * 1024

    /** The sidecar names to look for, in order: `<base>.lrc` then `<base>.LRC`. Empty for a name with no base. */
    fun sidecarNames(audioDisplayName: String?): List<String> {
        val name = audioDisplayName?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        if (base.isBlank() || base.contains('/')) return emptyList()
        return listOf("$base.lrc", "$base.LRC")
    }

    private val LegacyCharset = runCatching { charset("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    /** UTF-8 (with or without BOM); a file that is not valid UTF-8 is read as Windows-1252, the usual legacy encoding. */
    fun decode(bytes: ByteArray): String {
        val utf8 = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
        return (utf8 ?: String(bytes, LegacyCharset)).removePrefix("\uFEFF")
    }

    private val Timestamp = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val Synced = Regex("""^\[\d{2}:\d{2}\.\d{2,3}]""")
    /** ID tags (`[ar:…]`, `[ti:…]`, `[offset:…]`, `[length:…]`): not lyrics, and the player ignores them. */
    private val IdTag = Regex("""^\[[A-Za-z#]+:[^\]]*]$""")

    /**
     * The text the player can show, or null when the file holds no lyrics.
     *
     * Timestamps are rewritten to the `[mm:ss.xx]` form the parser reads (`LyricsUtils.TIME_REGEX`: `[1:05]`,
     * `[01:05]` and `[01:05:30]` become `[01:05.00]`/`[01:05.30]`), ID tags are dropped so synced lyrics start with
     * a timestamp, and line ends are normalised. Lyrics without any timestamp are kept as plain text, unless they
     * open with `[`, which the lyrics screen would take for synced lyrics and show empty.
     */
    fun normalize(raw: String): String? {
        val lines = raw.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { line -> normalizeTimestamps(line.trim()) }
            .filterNot { IdTag.matches(it) }
        val text = lines.joinToString("\n").trim()
        if (text.isEmpty()) return null
        if (lines.any { Synced.containsMatchIn(it) }) return text
        return text.takeUnless { it.startsWith("[") }
    }

    private fun normalizeTimestamps(line: String): String =
        Timestamp.replace(line) { match ->
            val (min, sec, frac) = match.destructured
            if (sec.toInt() > 59) return@replace match.value
            val fraction = when (frac.length) {
                0 -> "00"
                1 -> frac + "0"
                else -> frac
            }
            "[${min.padStart(2, '0')}:${sec.padStart(2, '0')}.$fraction]"
        }

    /**
     * The `ExternalStorageProvider` document id of [fileName] in the folder [relativePath] (MediaStore's
     * `RELATIVE_PATH`, e.g. `Music/Album/`) of [volumeName] (`external_primary` or an SD card id such as `1a2b-3c4d`),
     * when that file lies inside the tree [treeDocumentId] (`primary:Music`, `1A2B-3C4D:`). Null when it does not.
     */
    fun documentIdInTree(treeDocumentId: String, volumeName: String?, relativePath: String?, fileName: String): String? {
        val treeVolume = treeDocumentId.substringBefore(':', missingDelimiterValue = "")
        if (treeVolume.isEmpty()) return null
        val volume = volumeName?.trim().orEmpty()
        val sameVolume = if (treeVolume == "primary") {
            volume.isEmpty() || volume == "external" || volume == "external_primary"
        } else {
            volume.equals(treeVolume, ignoreCase = true)
        }
        if (!sameVolume) return null
        val folder = relativePath?.trim()?.trim('/').orEmpty()
        val path = if (folder.isEmpty()) fileName else "$folder/$fileName"
        val treePath = treeDocumentId.substringAfter(':').trim('/')
        val inside = treePath.isEmpty() || path.startsWith("$treePath/", ignoreCase = true)
        return if (inside) "$treeVolume:$path" else null
    }

    /**
     * Whether a STORED lyrics row of a song on this phone may be replaced by its `.lrc`: only a «no lyrics found»
     * row that nobody typed (nothing on screen is lost). A row with real lyrics stays as the user has it.
     */
    fun mayReplaceStored(isLocalSong: Boolean, storedLyrics: String, userEdited: Boolean, notFoundMarker: String): Boolean =
        isLocalSong && !userEdited && (storedLyrics == notFoundMarker || storedLyrics.isBlank())
}
