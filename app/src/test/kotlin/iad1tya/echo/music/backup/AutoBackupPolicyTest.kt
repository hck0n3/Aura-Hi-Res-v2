package iad1tya.echo.music.backup

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 908: names, rotation and the exclusion list of «Copia automática». */
class AutoBackupPolicyTest {
    @Test
    fun `a copy is named by its time and recognised as ours`() {
        val name = AutoBackupPolicy.fileName(LocalDateTime.of(2026, 11, 27, 9, 30, 5))
        assertEquals("Aura_auto_20261127093005.backup", name)
        assertTrue(AutoBackupPolicy.isAutoBackup(name))
        assertTrue("a provider's « (1)» still counts", AutoBackupPolicy.isAutoBackup("Aura_auto_20261127093005 (1).backup"))
        assertFalse("the manual copy is the user's", AutoBackupPolicy.isAutoBackup("Aura Hi-Res_20261127093005.backup"))
        assertFalse(AutoBackupPolicy.isAutoBackup("Aura_auto_2026.backup"))
        assertFalse(AutoBackupPolicy.isAutoBackup("Aura_auto_20261127093005.backup.txt"))
    }

    @Test
    fun `only the newest five automatic copies stay, and nothing else is ever deleted`() {
        val ours = (1..8).map { "Aura_auto_2026110${it}120000.backup" }
        val others = listOf("photo.jpg", "Aura Hi-Res_20250101000000.backup", "Aura_auto_notes.txt")
        val names = (others + ours).shuffled()
        val gone = AutoBackupPolicy.toDelete(names)
        assertEquals(ours.take(3).toSet(), gone.toSet())
        assertTrue(gone.none { it in others })
        assertEquals(emptyList<String>(), AutoBackupPolicy.toDelete(ours.take(5)))
        assertEquals(emptyList<String>(), AutoBackupPolicy.toDelete(others))
        assertEquals(5, AutoBackupPolicy.KEEP)
    }

    @Test
    fun `daily and weekly periods`() {
        assertEquals(1L, AutoBackupFrequency.DAILY.days)
        assertEquals(7L, AutoBackupFrequency.WEEKLY.days)
        assertEquals(listOf("DAILY", "WEEKLY"), AutoBackupFrequency.entries.map { it.name })
    }

    @Test
    fun `the exclusion list keeps out the session, cookies, visitorData, the licence and the master key`() {
        for (entry in listOf(
            "settings.preferences_pb", // InnerTubeCookieKey, VisitorDataKey, DataSyncIdKey, the account e-mail
            "datastore/settings.preferences_pb",
            "shared_prefs/jr_license.xml", // license/: licence key, master key, demo record
            "shared_prefs/license_cache.xml",
            "shared_prefs/master_key.xml",
            "shared_prefs/cookies.xml",
            "shared_prefs/spotify_session.xml",
            "shared_prefs/tidal_tokens.xml",
            "shared_prefs/visitor_data.xml",
            "files/rclone.conf",
            "no_backup/anything",
            "../song.db",
            "",
        )) assertFalse(entry, BackupExclusions.allows(entry))
        for (entry in listOf(
            "song.db",
            "shared_prefs/nanosonic_eq_profiles.xml",
            "shared_prefs/echo_eq_prefs.xml",
            "shared_prefs/eq_device_profiles.xml",
            "shared_prefs/echomusic_settings.xml",
        )) assertTrue(entry, BackupExclusions.allows(entry))
    }
}
