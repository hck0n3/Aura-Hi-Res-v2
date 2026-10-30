package iad1tya.echo.music.edge

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies what the edge Worker signed. WebCrypto (the Worker side) produces a signature as the raw 64 bytes r||s; Java wants it
 * as a DER sequence, so this converts first. ECDSA P-256 rather than Ed25519 because Ed25519 in `java.security` needs Android 13+
 * and this app supports 8.
 */
object EdgeSignature {
    fun verify(publicKeySpkiBase64: String, payload: String, signatureBase64: String): Boolean = try {
        if (publicKeySpkiBase64.isBlank()) {
            false
        } else {
            val raw = Base64.getDecoder().decode(signatureBase64.trim())
            val der = rawToDer(raw)
            if (der == null) {
                false
            } else {
                val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeySpkiBase64.trim())))
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(payload.toByteArray(Charsets.UTF_8))
                    verify(der)
                }
            }
        }
    } catch (_: Exception) {
        false
    }

    /** 64 raw bytes (r then s) → `SEQUENCE { INTEGER r, INTEGER s }`; null when the input is not 64 bytes. */
    fun rawToDer(raw: ByteArray): ByteArray? {
        if (raw.size != 64) return null
        val r = derInteger(raw.copyOfRange(0, 32))
        val s = derInteger(raw.copyOfRange(32, 64))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun derInteger(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
        var v = bytes.copyOfRange(start, bytes.size)
        if (v[0] < 0) v = byteArrayOf(0) + v // a leading 1 bit would read as negative
        return byteArrayOf(0x02, v.size.toByte()) + v
    }
}
