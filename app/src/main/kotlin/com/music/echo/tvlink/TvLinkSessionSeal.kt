package iad1tya.echo.music.tvlink

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Registry row 907: the session is sealed TWICE. Outside, with the QR's key ([TvLinkSeal]); inside, with an ephemeral ECDH
 * (P-256, JDK/Android crypto) between the phone and the key pair the TV made for this code and announced in its sealed hello.
 * So the QR alone is not enough: someone who photographs the TV's screen and listens on the relay still cannot open the
 * session, because the TV's private key never leaves the TV. Pure JVM, tested as is.
 */
object TvLinkKeys {
    fun generate(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()

    fun publicFrom(encoded: ByteArray): PublicKey? =
        runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) }.getOrNull()

    /** SHA-256 of the ECDH secret and the QR key under a domain prefix: both the TV's private key and the code are needed. */
    internal fun sessionKey(normalized: String, own: PrivateKey, peer: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own)
        agreement.doPhase(peer, true)
        return MessageDigest.getInstance("SHA-256").run {
            update("aura-tvlink-session:".toByteArray(Charsets.UTF_8))
            update(agreement.generateSecret())
            update(TvLinkCode.key(normalized))
            digest()
        }
    }
}

object TvLinkSessionSeal {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    /** Phone: seals [plain] for the TV whose public key came in its hello. */
    fun seal(normalized: String, tvPublic: PublicKey, plain: ByteArray, random: SecureRandom = SecureRandom()): String {
        val ephemeral = TvLinkKeys.generate()
        val key = TvLinkKeys.sessionKey(normalized, ephemeral.private, tvPublic)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(normalized))
        val sealed = cipher.doFinal(plain)
        val pub = ephemeral.public.encoded
        val inner = ByteBuffer.allocate(2 + pub.size + NONCE_BYTES + sealed.size)
            .putShort(pub.size.toShort()).put(pub).put(nonce).put(sealed).array()
        return TvLinkSeal.seal(normalized, TvLinkSeal.SESSION, inner, random)
    }

    /** TV: the plaintext, or null unless it was sealed with this code AND for this TV's key pair. */
    fun open(normalized: String, tvPrivate: PrivateKey, blob: String): ByteArray? = runCatching {
        val inner = TvLinkSeal.open(normalized, TvLinkSeal.SESSION, blob) ?: return null
        val buf = ByteBuffer.wrap(inner)
        val pubSize = buf.short.toInt()
        if (pubSize <= 0 || pubSize > buf.remaining() - NONCE_BYTES - TAG_BITS / 8) return null
        val pub = ByteArray(pubSize).also { buf.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buf.get(it) }
        val sealed = ByteArray(buf.remaining()).also { buf.get(it) }
        val peer = TvLinkKeys.publicFrom(pub) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(TvLinkKeys.sessionKey(normalized, tvPrivate, peer), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(normalized))
        cipher.doFinal(sealed)
    }.getOrNull()

    private fun aad(normalized: String): ByteArray =
        "aura-tvlink:session-inner:${TvLinkCode.relayId(normalized)}".toByteArray(Charsets.UTF_8)
}
