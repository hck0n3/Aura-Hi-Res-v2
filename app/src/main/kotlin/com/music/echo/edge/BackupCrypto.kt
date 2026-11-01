package iad1tya.echo.music.edge

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Function 5 of docs/PLAN_WORKER_9_FUNCIONES.md — the cloud backup is encrypted ON THE PHONE; the Worker only ever holds bytes it
 * cannot read. Both the address of the copy ([backupId]) and the key come from the same secret the user already has (the typed
 * pairing code, or the license key + purchase email), each through its own domain prefix, so neither one reveals the other, and
 * neither equals the sync room id the Worker already sees.
 *
 * File = `AURAB1` + salt(16) + iv(12) + AES-256-GCM(ciphertext + tag). The GCM tag makes a wrong secret or a damaged copy fail
 * loudly ([decrypt] returns null) instead of restoring garbage over the user's library.
 */
object BackupCrypto {
    const val MAGIC = "AURAB1"
    private const val SALT = 16
    private const val IV = 12
    private const val ITERATIONS = 120_000

    /** The address of the user's copies on the Worker: 64 hex characters, not reversible to the secret. */
    fun backupId(secret: String): String =
        MessageDigest.getInstance("SHA-256").digest("aura-backup-id:$secret".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun key(secret: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec("aura-backup-key:$secret".toCharArray(), salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun encrypt(secret: String, plain: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(secret, salt), GCMParameterSpec(128, iv)) }
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + cipher.doFinal(plain)
    }

    /** The original bytes, or null when this is not an Aura copy, is damaged, or [secret] is not the one that made it. */
    fun decrypt(secret: String, file: ByteArray): ByteArray? = try {
        val head = MAGIC.length
        if (file.size < head + SALT + IV + 16 || String(file, 0, head, Charsets.US_ASCII) != MAGIC) {
            null
        } else {
            val salt = file.copyOfRange(head, head + SALT)
            val iv = file.copyOfRange(head + SALT, head + SALT + IV)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(secret, salt), GCMParameterSpec(128, iv)) }
            cipher.doFinal(file, head + SALT + IV, file.size - head - SALT - IV)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The secret behind the copies: the typed pairing code when there is one, else the license key + purchase email; null when
     * neither exists (nothing to protect the copy with, so none is made).
     */
    fun secretFor(normalizedCode: String?, licenseKey: String?, email: String?): String? = when {
        normalizedCode != null -> "code:$normalizedCode"
        !licenseKey.isNullOrBlank() && !email.isNullOrBlank() -> "license:${licenseKey.trim()}|${email.trim().lowercase()}"
        else -> null
    }
}
