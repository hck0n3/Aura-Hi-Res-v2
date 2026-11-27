package iad1tya.echo.music.tvlink

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Registry row 907 («Iniciar sesión en la TV»): the one secret a TV shows (as a QR and as text) so the phone can hand it the
 * Google session. Pure JVM (no Android), so it is tested as is.
 *
 * 25 characters of the 31-letter alphabet of [iad1tya.echo.music.devicesync.SyncCode] (no 0/O, 1/I/L) ≈ 124 bits, written in
 * groups of five so it never looks like a pairing code (groups of four). The relay (docs/worker/rooms, the tvlink routes) only ever
 * sees [relayId], a hash of the code: the key ([key]) never leaves the two devices, so the relay can neither read nor forge
 * what goes through it.
 */
object TvLinkCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 25

    /** How long the relay keeps a code alive after the TV's hello (the Worker's TVLINK_TTL_MS). */
    const val TTL_MS = 120_000L

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** The code as it is hashed (upper case, only the alphabet), or null when it is not a full code. */
    fun normalize(input: String): String? {
        val clean = input.uppercase().filter { it in ALPHABET }
        return if (clean.length == LENGTH) clean else null
    }

    /** What the field shows while the code is typed or pasted: upper case, only the alphabet, a dash every five. */
    fun typing(input: String): String =
        input.uppercase().filter { it in ALPHABET }.take(LENGTH).chunked(5).joinToString("-")

    /** `ABCDE-FGHJK-…` for showing a normalized code. */
    fun pretty(normalized: String): String = normalized.chunked(5).joinToString("-")

    /** What the QR carries; any camera app opens it in Aura (`echomusic://tvlogin`, AndroidManifest), as with row 615. */
    fun link(normalized: String): String = "echomusic://tvlogin?c=" + pretty(normalized)

    /** The normalized code inside a TV link, or null when [link] is not one (or its code is not a full code). */
    fun fromLink(link: String): String? {
        val m = Regex("^echomusic://tvlogin\\?c=([A-Za-z0-9-]+)$").find(link.trim()) ?: return null
        return normalize(m.groupValues[1])
    }

    /** The relay's name for this code: 32 hex of SHA-256 with its own domain prefix. Cannot be turned back into the code. */
    fun relayId(normalized: String): String = sha256("aura-tvlink-id:$normalized").joinToString("") { "%02x".format(it) }.take(32)

    /** The AES-256 key, from the same code under a different prefix. Never sent, never logged. */
    internal fun key(normalized: String): ByteArray = sha256("aura-tvlink-key:$normalized")

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
}

/**
 * AES-256-GCM with the code's key. The blob is base64(nonce ‖ ciphertext ‖ tag); the additional data names the purpose and the
 * relay id, so a hello can never be replayed as a session (or a blob moved to another code).
 */
object TvLinkSeal {
    const val HELLO = "hello"
    const val SESSION = "session"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(normalized: String, purpose: String, plain: ByteArray, random: SecureRandom = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(TvLinkCode.key(normalized), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(normalized, purpose))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain))
    }

    /** The plaintext, or null when [blob] was not sealed with this code for this purpose (or was altered). */
    fun open(normalized: String, purpose: String, blob: String): ByteArray? = runCatching {
        val bytes = Base64.getDecoder().decode(blob)
        if (bytes.size <= NONCE_BYTES + TAG_BITS / 8) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(TvLinkCode.key(normalized), "AES"),
            GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES),
        )
        cipher.updateAAD(aad(normalized, purpose))
        cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
    }.getOrNull()

    private fun aad(normalized: String, purpose: String): ByteArray =
        "aura-tvlink:$purpose:${TvLinkCode.relayId(normalized)}".toByteArray(Charsets.UTF_8)
}
