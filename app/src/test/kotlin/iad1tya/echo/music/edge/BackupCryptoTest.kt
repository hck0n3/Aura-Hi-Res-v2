package iad1tya.echo.music.edge

import iad1tya.echo.music.devicesync.SyncCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val data = ByteArray(5000) { (it * 31).toByte() }

    @Test
    fun `a copy opens with its own secret and with nothing else`() {
        val file = BackupCrypto.encrypt("code:AAAA", data)
        assertTrue(String(file, 0, 6, Charsets.US_ASCII) == "AURAB1")
        assertArrayEquals(data, BackupCrypto.decrypt("code:AAAA", file))
        assertNull(BackupCrypto.decrypt("code:BBBB", file))
    }

    @Test
    fun `a damaged or foreign file never opens`() {
        val file = BackupCrypto.encrypt("s", data)
        val flipped = file.copyOf().also { it[file.size - 3] = (it[file.size - 3] + 1).toByte() }
        assertNull(BackupCrypto.decrypt("s", flipped))
        assertNull(BackupCrypto.decrypt("s", file.copyOf(40)))
        assertNull(BackupCrypto.decrypt("s", "PK-zip-bytes-not-aura-".repeat(5).toByteArray()))
        assertNull(BackupCrypto.decrypt("s", ByteArray(0)))
    }

    @Test
    fun `two copies of the same data differ, so equal libraries are not recognisable`() {
        assertNotEquals(BackupCrypto.encrypt("s", data).toList(), BackupCrypto.encrypt("s", data).toList())
    }

    @Test
    fun `the address of the copies is not the sync room and reveals nothing of the code`() {
        val code = SyncCode.normalize("ABCD-EFGH-JKMN-PQRS-TUVW")!!
        val id = BackupCrypto.backupId(BackupCrypto.secretFor(code, null, null)!!)
        assertEquals(64, id.length)
        assertNotEquals(SyncCode.groupId(code), id)
        assertTrue(!id.contains(code))
    }

    @Test
    fun `the secret is the typed code first, then the license and email, else none`() {
        assertEquals("code:X", BackupCrypto.secretFor("X", "KEY", "a@b.c"))
        assertEquals("license:KEY|a@b.c", BackupCrypto.secretFor(null, " KEY ", " A@B.C "))
        assertNull(BackupCrypto.secretFor(null, "KEY", ""))
        assertNull(BackupCrypto.secretFor(null, null, null))
    }
}
