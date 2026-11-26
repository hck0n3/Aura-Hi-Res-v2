package iad1tya.echo.music.cloud

import java.io.File
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 889: the cloud tokens (rclone.conf) are encrypted on disk with rclone's own encryption, the password is
 * wrapped by a keystore key, and nothing of it reaches a backup (Android's or the in-app one).
 */
class CloudConfigAtRestTest {
    private fun aesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `the wrapped password round-trips and nothing else opens it`() {
        val key = aesKey()
        val password = WrappedSecret.newPassword()
        val blob = WrappedSecret.seal(key, password.toByteArray())
        assertEquals(password, WrappedSecret.open(key, blob)!!.toString(Charsets.UTF_8))
        assertFalse("the password is not stored in the clear", String(blob, Charsets.ISO_8859_1).contains(password))
        assertNull("another key", WrappedSecret.open(aesKey(), blob))
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull("GCM rejects an altered blob", WrappedSecret.open(key, tampered))
        assertNotEquals("a fresh IV each time", blob.toList(), WrappedSecret.seal(key, password.toByteArray()).toList())
    }

    @Test
    fun `the blob format is versioned and rejects garbage`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = byteArrayOf(9, 8, 7)
        val (i, c) = WrappedSecret.decode(WrappedSecret.encode(iv, ct))!!
        assertArrayEquals(iv, i)
        assertArrayEquals(ct, c)
        assertNull(WrappedSecret.decode(ByteArray(0)))
        assertNull(WrappedSecret.decode(byteArrayOf(2, 12) + iv + ct))
        assertNull(WrappedSecret.decode(byteArrayOf(1, 12) + iv))
        assertNull(WrappedSecret.decode(byteArrayOf(1, 0, 5)))
    }

    @Test
    fun `the password survives rclone's trimming and line reading`() {
        repeat(50) {
            val p = WrappedSecret.newPassword(SecureRandom())
            assertEquals(43, p.length)
            assertEquals(p, p.trim())
            assertTrue(p.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }

    @Test
    fun `only a config rclone itself encrypted counts as encrypted`() {
        assertTrue(RcloneConfigFiles.isEncrypted("# Encrypted rclone configuration File\n\nRCLONE_ENCRYPT_V0:\nAbCd==\n"))
        assertTrue(RcloneConfigFiles.isEncrypted("RCLONE_ENCRYPT_V0:\nxyz"))
        assertTrue(RcloneConfigFiles.isEncrypted("; note\n  RCLONE_ENCRYPT_V0:  \n"))
        assertFalse("empty is plaintext to rclone", RcloneConfigFiles.isEncrypted(""))
        assertFalse(RcloneConfigFiles.isEncrypted("[aura-onedrive]\ntype = onedrive\ntoken = {}\nRCLONE_ENCRYPT_V0:\n"))
        assertFalse("another version is not ours", RcloneConfigFiles.isEncrypted("RCLONE_ENCRYPT_V1:\n"))
        assertEquals("RCLONE_ENCRYPT_V0:", RcloneConfigFiles.ENCRYPTED_MARKER)
    }

    // ── Source guards: no Android SDK in the unit job, so the wiring is pinned on the text ──

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val store by lazy { read("app/src/main/kotlin/com/music/echo/cloud/RcloneConfigStore.kt") }

    @Test
    fun `the config and its key live in noBackupFilesDir and are encrypted by rclone before the daemon may use them`() {
        assertTrue(store.contains("fun dir(context: Context): File = File(context.noBackupFilesDir, DIR)"))
        listOf("context.filesDir", "getExternalFilesDir", "externalCacheDir", "context.dataDir").forEach {
            assertFalse("only noBackupFilesDir (and cacheDir for rclone's scratch): $it", store.contains(it))
        }
        assertTrue("rclone's own encryption", store.contains("ProcessBuilder(binary.path, \"config\", \"encryption\", \"set\", \"--config=\${config.path}\")"))
        assertTrue("the password typed twice on stdin", store.contains("write(\"\$password\\n\$password\\n\""))
        assertTrue("never used unencrypted", store.contains("check(config.isFile && RcloneConfigFiles.isEncrypted(config.readText()))"))
        assertTrue("a plaintext config is thrown away", store.contains("if (config.isFile && !RcloneConfigFiles.isEncrypted(config.readText())) {"))
        assertTrue("keystore AES-GCM", store.contains("KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, \"AndroidKeyStore\")"))
        assertTrue(store.contains(".setBlockModes(KeyProperties.BLOCK_MODE_GCM)"))
        assertTrue("an inherited RCLONE_ variable never reaches rclone", store.contains("env.keys.filter { it.startsWith(\"RCLONE_\") }.forEach { env.remove(it) }"))
    }

    @Test
    fun `nothing secret is logged`() {
        val logs = Regex("""Timber\.tag\(TAG\)\.[a-z]\(([^\n]*)\)""").findAll(store).map { it.groupValues[1] }.toList()
        assertTrue("it does log its outcome", logs.isNotEmpty())
        for (line in logs) {
            for (secret in listOf("password", "path", "config", "readText", "\$")) {
                assertFalse("logs $secret: $line", line.substringAfter("\",", "").contains(secret))
            }
        }
        assertFalse("rclone's output is never kept", store.contains("readBytes().toString") || store.contains("bufferedReader"))
    }

    @Test
    fun `no backup carries the cloud config`() {
        // Android Auto Backup / device transfer never copy noBackupFilesDir; the rules must not try to pull it in.
        for (rules in listOf("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml")) {
            val text = read(rules)
            assertFalse("$rules includes files selectively", text.contains("<include"))
            assertFalse(text.contains("no_backup"))
            assertFalse(text.contains("rclone"))
        }
        // The in-app backup copies song.db and a whitelist of shared_prefs, nothing under files/ or no_backup/.
        val backup = read("app/src/main/kotlin/com/music/echo/viewmodels/BackupRestoreViewModel.kt")
        assertFalse(backup.contains("noBackupFilesDir"))
        assertFalse(backup.contains("rclone"))
        assertFalse(backup.contains("cloud/"))
        val entries = Regex("""putNextEntry\(ZipEntry\(([^)]*)\)\)""").findAll(backup).map { it.groupValues[1] }.toList()
        assertEquals(listOf("InternalDatabase.DB_NAME", "\"shared_prefs/\$name.xml\""), entries)
    }
}
