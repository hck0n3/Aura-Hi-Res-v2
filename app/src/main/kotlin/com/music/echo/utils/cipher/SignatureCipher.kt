package iad1tya.echo.music.utils.cipher

import java.io.ByteArrayOutputStream

/**
 * The `signatureCipher` of a stream format: a query string `s=<obfuscated signature>&sp=<param name>&url=<base url>`.
 * Pure Kotlin (no android.net.Uri) so the parsing is covered by JVM unit tests; the signature itself is solved elsewhere
 * (the player's own code, see EjsNTransformSolver).
 */
internal object SignatureCipher {

    data class Parts(val s: String, val sp: String, val url: String)

    fun parse(cipher: String): Parts? {
        val values = HashMap<String, String>()
        for (pair in cipher.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            values[percentDecode(pair.substring(0, eq))] = percentDecode(pair.substring(eq + 1))
        }
        val s = values["s"]?.takeIf { it.isNotEmpty() } ?: return null
        val url = values["url"]?.takeIf { it.isNotEmpty() } ?: return null
        return Parts(s = s, sp = values["sp"]?.takeIf { it.isNotEmpty() } ?: "signature", url = url)
    }

    /** The stream URL with the solved signature appended under the parameter name the cipher asked for. */
    fun buildUrl(parts: Parts, solvedSignature: String): String {
        val separator = if (parts.url.contains('?')) "&" else "?"
        return parts.url + separator + parts.sp + "=" + percentEncode(solvedSignature)
    }

    /** Percent-decoding as android.net.Uri.decode does it: UTF-8, and a literal '+' stays a '+'. */
    fun percentDecode(value: String): String {
        if (value.indexOf('%') < 0) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hi = Character.digit(value[i + 1], 16)
                val lo = Character.digit(value[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            val cp = value.codePointAt(i)
            out.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
            i += Character.charCount(cp)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Everything but the RFC 3986 unreserved characters is percent-encoded (UTF-8). */
    fun percentEncode(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val ch = b.toInt() and 0xFF
            val unreserved = (ch in 'A'.code..'Z'.code) || (ch in 'a'.code..'z'.code) || (ch in '0'.code..'9'.code) ||
                ch == '-'.code || ch == '_'.code || ch == '.'.code || ch == '~'.code
            if (unreserved) sb.append(ch.toChar()) else sb.append('%').append("%02X".format(ch))
        }
        return sb.toString()
    }
}

/** The signatureTimestamp written in a player.js (`signatureTimestamp:20352`), read the way yt-dlp reads it. Pure. */
internal object PlayerSts {
    private val SIGNATURE_TIMESTAMP = Regex("""signatureTimestamp\s*:\s*(\d{5})""")
    private val STS = Regex("""\bsts\s*:\s*(\d{5})""")

    fun parse(playerJs: String): Int? =
        (SIGNATURE_TIMESTAMP.find(playerJs) ?: STS.find(playerJs))?.groupValues?.get(1)?.toIntOrNull()
}
