package iad1tya.echo.music.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import timber.log.Timber

/**
 * Registry row 889 — the cloud accounts' rclone.conf at rest.
 *
 * WHERE: `noBackupFilesDir/cloud/`. Android's Auto Backup and device transfer never copy `noBackupFilesDir`, and the
 * in-app backup (BackupRestoreViewModel) copies only song.db and a whitelist of shared_prefs, so the OAuth tokens never
 * leave this phone (CloudBackupExclusionGuardTest pins both).
 *
 * HOW (decision, row 889): rclone's OWN config encryption (`RCLONE_ENCRYPT_V0`, NaCl secretbox keyed by
 * SHA-256 of a password), not a file Aura encrypts itself. Encrypting the file ourselves would force a plaintext copy on
 * disk for as long as the daemon runs (rclone rewrites the file on every token refresh), which is the exact window a
 * leak needs. With rclone's own encryption the file is never plaintext on disk: the daemon reads the password from
 * RCLONE_CONFIG_PASS and re-encrypts on every save. The password is 32 random bytes, kept only wrapped by an
 * AndroidKeyStore AES-256-GCM key that never leaves the keystore.
 *
 * A fresh file is encrypted from the start by `rclone config encryption set`, the password written twice to its stdin
 * (rclone reads a non-terminal stdin line by line, fs/config/ui.go ReadLine). The daemon refuses to start on a config
 * that is not encrypted ([RcloneConfigFiles.isEncrypted]); a plaintext or orphaned one is deleted, which only means
 * «Vuelve a conectar».
 *
 * Never logged: the password, the paths, the file contents, rclone's output (AGENTS rule 4).
 */
object RcloneConfigFiles {
    const val DIR = "cloud"
    const val CONFIG = "rclone.conf"
    const val WRAPPED_PASSWORD = "rclone.key"

    /** The first line rclone writes for an encrypted config (fs/config/crypt.go, Encrypt). */
    const val ENCRYPTED_MARKER = "RCLONE_ENCRYPT_V0:"

    /**
     * Pure. Mirrors rclone's Decrypt: the first line that is neither blank nor a `;`/`#` comment decides. Anything else
     * (an empty file included) is a plaintext config to rclone.
     */
    fun isEncrypted(text: String): Boolean {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
            return line == ENCRYPTED_MARKER
        }
        return false
    }

    fun dir(context: Context): File = File(context.noBackupFilesDir, DIR)
}

/** Pure: the password wrapped by a keystore key, stored as `version | ivLength | iv | ciphertext+tag`. */
object WrappedSecret {
    private const val VERSION: Byte = 1
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.isNotEmpty() && iv.size < 256 && ciphertext.isNotEmpty())
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    /** (iv, ciphertext), or null for anything that is not a blob [encode] wrote. */
    fun decode(blob: ByteArray): Pair<ByteArray, ByteArray>? {
        if (blob.size < 3 || blob[0] != VERSION) return null
        val ivLength = blob[1].toInt() and 0xff
        if (ivLength == 0 || blob.size <= 2 + ivLength) return null
        return blob.copyOfRange(2, 2 + ivLength) to blob.copyOfRange(2 + ivLength, blob.size)
    }

    /** The cipher picks the IV (an AndroidKeyStore key refuses a caller-supplied one). */
    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return encode(cipher.iv, cipher.doFinal(plain))
    }

    /** null when the blob is malformed, the key is another one or the data was altered (GCM tag). */
    fun open(key: SecretKey, blob: ByteArray): ByteArray? {
        val (iv, ciphertext) = decode(blob) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    /** 32 random bytes as URL-safe Base64 without padding: no whitespace (rclone trims) and no newline (stdin lines). */
    fun newPassword(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object RcloneConfigStore {
    private const val TAG = "Cloud"
    private const val KEY_ALIAS = "aura_cloud_rclone_config_v1"
    private const val BOOTSTRAP_TIMEOUT_S = 20L

    class Prepared(val config: File, val password: String)

    /**
     * Blocking (call off the main thread). Returns the encrypted config and its password, creating both the first time.
     * Throws when the config cannot be made encrypted; the daemon is then not started.
     */
    fun prepare(context: Context, binary: File): Prepared {
        val dir = RcloneConfigFiles.dir(context).apply { mkdirs() }
        val config = File(dir, RcloneConfigFiles.CONFIG)
        val keyFile = File(dir, RcloneConfigFiles.WRAPPED_PASSWORD)
        val key = keystoreKey()

        var password = keyFile.takeIf { it.isFile }
            ?.let { runCatching { it.readBytes() }.getOrNull() }
            ?.let { WrappedSecret.open(key, it) }
            ?.toString(Charsets.UTF_8)
        if (password == null) {
            // No password, or one this keystore cannot open any more: a config left beside it is unreadable for good.
            if (config.exists()) Timber.tag(TAG).w("cloud: config without a usable key, reset (sign-in needed)")
            config.delete()
            password = WrappedSecret.newPassword()
            writeAtomically(keyFile, WrappedSecret.seal(key, password.toByteArray(Charsets.UTF_8)))
        }
        if (config.isFile && !RcloneConfigFiles.isEncrypted(config.readText())) {
            Timber.tag(TAG).w("cloud: config was not encrypted, reset (sign-in needed)")
            config.delete()
        }
        if (!config.isFile) bootstrap(context, binary, config, password)
        check(config.isFile && RcloneConfigFiles.isEncrypted(config.readText())) { "cloud config is not encrypted" }
        return Prepared(config, password)
    }

    /** Forget everything: config, wrapped password. The keystore key stays (it wraps nothing any more). */
    fun wipe(context: Context) {
        val dir = RcloneConfigFiles.dir(context)
        File(dir, RcloneConfigFiles.CONFIG).delete()
        File(dir, RcloneConfigFiles.WRAPPED_PASSWORD).delete()
    }

    /** `rclone config encryption set` on a config that does not exist yet: writes an empty, encrypted one. */
    private fun bootstrap(context: Context, binary: File, config: File, password: String) {
        val builder = ProcessBuilder(binary.path, "config", "encryption", "set", "--config=${config.path}")
            .redirectErrorStream(true)
        RcloneEnvironment.apply(context, builder.environment())
        val process = builder.start()
        // rclone's output is never kept or logged (it can name the path); it is read only so the pipe never fills.
        val drain = Thread { runCatching { process.inputStream.use { s -> val b = ByteArray(1024); while (s.read(b) >= 0) {} } } }
            .apply { isDaemon = true; start() }
        runCatching {
            process.outputStream.use { it.write("$password\n$password\n".toByteArray(Charsets.UTF_8)) }
        }
        val finished = process.waitFor(BOOTSTRAP_TIMEOUT_S, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        drain.join(1000)
        Timber.tag(TAG).i("cloud: config bootstrap exit=%d", if (finished) process.exitValue() else -1)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "could not store the cloud key" }
        }
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/** The environment every rclone process gets: a writable HOME and TMPDIR inside the app (Android sets neither usefully). */
object RcloneEnvironment {
    fun apply(context: Context, env: MutableMap<String, String>) {
        env.keys.filter { it.startsWith("RCLONE_") }.forEach { env.remove(it) }
        val home = File(RcloneConfigFiles.dir(context), "home").apply { mkdirs() }
        val tmp = File(context.cacheDir, "rclone-tmp").apply { mkdirs() }
        env["HOME"] = home.path
        env["TMPDIR"] = tmp.path
        env["XDG_CACHE_HOME"] = File(context.cacheDir, "rclone").apply { mkdirs() }.path
    }
}
