package iad1tya.echo.music.license

import iad1tya.echo.music.devicesync.SyncCode
import iad1tya.echo.music.edge.BackupCrypto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 851 (owner order 2026-11-24: «quita el emparejamiento por correo a quien entra a ella (la clave maestra)»): a master
 * key never pairs devices by email, while cloud backup keeps the key + email it already used so nobody loses their copies.
 */
class MasterKeyPolicyTest {
    private val identity = "MASTER-KEY" to "owner@mail.com"

    @Test
    fun `a master key gives device sync no identity, so there is no auto group`() {
        val sync = MasterKeyPolicy.syncIdentity(identity, masterKey = true)
        assertNull(sync)
        assertNull(SyncCode.autoGroupId(sync?.first, sync?.second))
    }

    @Test
    fun `a Gumroad buyer whose email the Worker confirmed keeps pairing by license exactly as before`() {
        val buyer = "KEY-123" to "buyer@mail.com"
        val sync = MasterKeyPolicy.syncIdentity(buyer, masterKey = false)
        assertEquals(buyer, sync)
        assertEquals(SyncCode.autoGroupId("KEY-123", "buyer@mail.com"), SyncCode.autoGroupId(sync?.first, sync?.second))
        assertNotNull(SyncCode.autoGroupId(sync?.first, sync?.second))
    }

    @Test
    fun `no stored identity stays none`() {
        assertNull(MasterKeyPolicy.syncIdentity(null, masterKey = false))
        assertNull(MasterKeyPolicy.syncIdentity(null, masterKey = true))
    }

    @Test
    fun `the cloud backup secret of a master key user is unchanged`() {
        // Backup still derives its space from LicenseManager.purchaseIdentity (key + email), not from the sync identity.
        assertEquals("license:MASTER-KEY|owner@mail.com", BackupCrypto.secretFor(null, identity.first, identity.second))
    }

    @Test
    fun `the master mark is sticky for the same key and follows the answer for a new key`() {
        assertTrue(MasterKeyPolicy.markAfterActive(wasMaster = false, saysMaster = true, sameKey = true))
        // a Worker rolled back to one that no longer says master must not turn pairing by email back on
        assertTrue(MasterKeyPolicy.markAfterActive(wasMaster = true, saysMaster = false, sameKey = true))
        assertFalse(MasterKeyPolicy.markAfterActive(wasMaster = false, saysMaster = false, sameKey = true))
        // another key activated: a Gumroad key clears the mark, a master key sets it
        assertFalse(MasterKeyPolicy.markAfterActive(wasMaster = true, saysMaster = false, sameKey = false))
        assertTrue(MasterKeyPolicy.markAfterActive(wasMaster = false, saysMaster = true, sameKey = false))
    }

    @Test
    fun `a master key keeps the typed email for the cloud backup while device sync still gets no identity`() {
        // Reinstall or new phone: the copies made before row 850 live under license:<key>|<email>, so the email must be kept.
        val kept = MasterKeyPolicy.emailToKeep("owner@mail.com", emailVerified = false, masterKey = true)
        assertEquals("owner@mail.com", kept)
        assertEquals("license:MASTER-KEY|owner@mail.com", BackupCrypto.secretFor(null, "MASTER-KEY", kept))
        assertNull(MasterKeyPolicy.syncIdentity("MASTER-KEY" to kept!!, masterKey = true))
        // the old Worker accepted only a typed email with an "@": anything else never had copies and is not kept
        assertNull(MasterKeyPolicy.emailToKeep("not-an-email", emailVerified = false, masterKey = true))
        assertNull(MasterKeyPolicy.emailToKeep(null, emailVerified = false, masterKey = true))
        assertNull(MasterKeyPolicy.emailToKeep("  ", emailVerified = false, masterKey = true))
    }

    @Test
    fun `a Gumroad key keeps the email only when the Worker confirmed it, as before`() {
        assertEquals("buyer@mail.com", MasterKeyPolicy.emailToKeep("buyer@mail.com", emailVerified = true, masterKey = false))
        assertNull(MasterKeyPolicy.emailToKeep("someone@mail.com", emailVerified = false, masterKey = false))
        assertNull(MasterKeyPolicy.emailToKeep(null, emailVerified = true, masterKey = false))
    }

    // --- wiring: who uses which identity ---

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val main = "app/src/main/kotlin/com/music/echo"

    @Test
    fun `device sync pairs by the sync identity and cloud backup keeps the purchase identity`() {
        val engine = read("$main/devicesync/DeviceSyncEngine.kt")
        assertTrue(engine.contains("val identity = iad1tya.echo.music.license.LicenseManager.syncIdentity(appContext)"))
        assertFalse("device sync must not pair by the raw purchase identity", engine.contains("LicenseManager.purchaseIdentity("))
        val backup = read("$main/ui/screens/settings/BackupAndRestore.kt")
        assertTrue(backup.contains("val identity = iad1tya.echo.music.license.LicenseManager.purchaseIdentity(context)"))
    }

    @Test
    fun `a master key that typed a pairing code still restores the copies made under key and email`() {
        val backup = read("$main/ui/screens/settings/BackupAndRestore.kt")
        assertTrue(backup.contains("if (!iad1tya.echo.music.license.LicenseManager.isMasterKey(context)) return null"))
        assertTrue(backup.contains("return iad1tya.echo.music.edge.BackupCrypto.secretFor(null, identity.first, identity.second)"))
        assertTrue(backup.contains("if (download.exceptionOrNull()?.message == \"no-backup\" && older != null && older != secret) {"))
        assertTrue(backup.contains("download = iad1tya.echo.music.edge.EdgeClient.backupDownload(context, older, tmp)"))
        // the code still wins first: the space a typed code points to is tried before the older one
        assertEquals("code:ABC", BackupCrypto.secretFor("ABC", "MASTER-KEY", "owner@mail.com"))
    }

    @Test
    fun `the license manager marks the key from every ACTIVE answer and the screen says why it does not pair`() {
        val manager = read("$main/license/LicenseManager.kt")
        assertTrue(manager.contains("MasterKeyPolicy.syncIdentity(purchaseIdentity(context), isMasterKey(context))"))
        assertTrue(manager.contains("saveMaster(context, MasterKeyPolicy.markAfterActive(isMasterKey(context), result.masterKey, sameKey = true))"))
        assertTrue(manager.contains("saveMaster(context, MasterKeyPolicy.markAfterActive(isMasterKey(context), result.masterKey, sameKey))"))
        assertTrue(manager.contains("saveEmail(context, MasterKeyPolicy.emailToKeep(email, result.emailVerified, isMasterKey(context)))"))
        assertFalse("a master key must keep its typed email for the cloud backup", manager.contains("email.takeIf { result.emailVerified }"))
        val client = read("$main/license/LicenseBackendClient.kt")
        assertTrue(client.contains("active && LicenseStatusParser.masterKey(body)"))
        val settings = read("$main/ui/screens/settings/integrations/DeviceSyncSettings.kt")
        assertTrue(settings.contains("masterKey -> stringResource(R.string.device_sync_auto_master_key)"))
        val es = read("app/src/main/res/values-es/echo_strings.xml")
        assertTrue(es.contains("Con una clave maestra los aparatos no se emparejan solos: usa un código de emparejamiento"))
    }
}
