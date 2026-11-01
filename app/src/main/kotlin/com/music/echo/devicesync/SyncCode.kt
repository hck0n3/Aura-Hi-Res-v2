package iad1tya.echo.music.devicesync

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The secret that pairs a user's devices (row 528). It is typed or pasted from one device into the others, never sent anywhere:
 * what the Worker sees is its SHA-256 ([groupId]), which cannot be turned back into the code and names the room.
 *
 * 20 characters of a 31-letter alphabet without the look-alikes (no 0/O, 1/I/L) ≈ 99 bits, written in groups of four.
 */
object SyncCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 20

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }.chunked(4).joinToString("-")

    /** The code as it is compared and hashed (upper case, only the alphabet), or null when it is not a full code. */
    fun normalize(input: String): String? {
        val clean = input.uppercase().filter { it in ALPHABET }
        return if (clean.length == LENGTH) clean else null
    }

    /**
     * The room of a user who needs no code (row 531): their own license key + purchase email, both typed at activation. The key is
     * secret and high-entropy, so the hash cannot be guessed or reversed; two devices of the same buyer get the same room without
     * the user doing anything. Null when either is missing — then only a typed code can pair.
     */
    fun autoGroupId(licenseKey: String?, email: String?): String? {
        val key = licenseKey?.trim().orEmpty()
        val mail = email?.trim()?.lowercase().orEmpty()
        if (key.isEmpty() || mail.isEmpty()) return null
        return MessageDigest.getInstance("SHA-256").digest("aura-sync-auto:$key|$mail".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Row 609 (owner 2026-10-31: «al escribir el código que se pongan los guiones solos»): what the field shows while a code is TYPED or
     * pasted — upper case, only the alphabet, at most [LENGTH] characters, a dash every four. Pasting an already dashed code gives the same text.
     */
    fun typing(input: String): String =
        input.uppercase().filter { it in ALPHABET }.take(LENGTH).chunked(4).joinToString("-")

    /** Row 615: what the QR of a pairing code carries; any camera app opens it in Aura (`echomusic://pair`, AndroidManifest). */
    fun pairLink(normalized: String): String = "echomusic://pair?code=" + pretty(normalized)

    /** The normalized code inside a pairing link, or null when [link] is not one (or its code is not a full code). */
    fun fromPairLink(link: String): String? {
        val m = Regex("^echomusic://pair\\?code=([A-Za-z0-9-]+)$").find(link.trim()) ?: return null
        return normalize(m.groupValues[1])
    }

    /** `AAAA-BBBB-…` for showing a normalized code. */
    fun pretty(normalized: String): String = normalized.chunked(4).joinToString("-")

    /** The room id: SHA-256 (hex) of the normalized code, with a domain prefix so it can never collide with another use of the code. */
    fun groupId(normalized: String): String =
        MessageDigest.getInstance("SHA-256").digest("aura-sync:$normalized".toByteArray()).joinToString("") { "%02x".format(it) }

    /** `wss://host/sync?g=<groupId>` from whatever the user pasted (https://…, wss://…, bare host, with or without `/sync`). */
    fun socketUrl(server: String, groupId: String): String? {
        var s = server.trim().removeSuffix("/")
        if (s.isEmpty()) return null
        s = s.removeSuffix("/sync").removeSuffix("/")
        val base = when {
            s.startsWith("wss://") || s.startsWith("ws://") -> s
            s.startsWith("https://") -> "wss://" + s.removePrefix("https://")
            s.startsWith("http://") -> "ws://" + s.removePrefix("http://")
            else -> "wss://$s"
        }
        if (base.length < 8 || base.contains(' ')) return null
        return "$base/sync?g=$groupId"
    }
}
