package iad1tya.echo.music.recognition

import kotlin.math.log10

/**
 * Row 642 (owner 2026-11-02: «el reconocedor de música falla, no me reconoce las canciones cuando las pongo, pero no siempre»). The shared log
 * said only `Status: No match` — nothing that tells «the mic heard silence» from «Shazam does not know it» from «the network died». One line
 * per attempt, NUMBERS AND CATEGORIES ONLY (AGENTS rule 4: no title, artist, id, URL or error text), so a few shared logs classify the failures.
 */
internal object RecognitionDiag {
    fun sourceName(audioSource: Int): String = when (audioSource) {
        9 -> "unprocessed"
        6 -> "voice_rec"
        1 -> "mic"
        else -> "other"
    }

    /** dBFS of a 16-bit amplitude, rounded; -99 for digital silence. */
    fun levelDb(amplitude: Double): Int =
        if (amplitude <= 0.0) -99 else (20.0 * log10(amplitude / 32768.0)).toInt().coerceIn(-99, 0)

    /** The kind of a failed window, from its message — the message itself is never logged. */
    fun errorCategory(message: String?): String {
        val m = message.orEmpty().lowercase()
        return when {
            m.startsWith("failed to resample") -> "resample"
            m.startsWith("invalid audio") -> "format"
            m.startsWith("failed to generate") -> "fingerprint"
            "429" in m || "too many" in m || "rate" in m && "limit" in m -> "limit"
            "timeout" in m || "timed out" in m || "unable to resolve" in m || "socket" in m || "connect" in m ||
                "ssl" in m || "network" in m || "unknownhost" in m -> "network"
            else -> "service"
        }
    }

    /** One window: `3s:nomatch:412ms`. */
    fun window(checkpointMs: Long, category: String, tookMs: Long): String = "${checkpointMs / 1000}s:$category:${tookMs}ms"

    /**
     * A silent recording (peak below about -50 dBFS) cannot be fingerprinted: it is a capture problem, not a catalogue one. The line says
     * which of the two this was so a «No match» is not blamed on Shazam when the microphone heard nothing.
     */
    fun verdict(result: String, peakDb: Int): String = when {
        result == "match" -> "match"
        peakDb <= SILENT_PEAK_DB -> "silent-capture"
        else -> result
    }

    fun line(source: String, recordedMs: Long, peakDb: Int, rmsDb: Int, windows: List<String>, result: String, elapsedMs: Long): String =
        "RECOGNITION_DIAG src=$source recordedMs=$recordedMs peakDb=$peakDb rmsDb=$rmsDb windows=${windows.size} " +
            "[${windows.joinToString(",")}] result=${verdict(result, peakDb)} elapsedMs=$elapsedMs"

    const val SILENT_PEAK_DB = -50
}
