package iad1tya.echo.music.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 908 («Copia automática»): source guards for the backup builder and the worker that a unit test cannot run.
 * The automatic copy is the manual backup's ZIP (row 15: never the settings DataStore), written only with entries
 * [BackupExclusions] allows, by WorkManager with constraints (rule 7), and restored through the unchanged restore (row 19).
 */
class AutoBackupGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val viewModel by lazy { main("viewmodels/BackupRestoreViewModel.kt") }

    /** Code lines only: KDoc and `//` comments may name what is excluded. */
    private fun code(text: String) = text.lines().map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        .joinToString("\n")

    @Test
    fun `the backup builder writes song db and the whitelist, each entry through the exclusion list`() {
        val builder = viewModel.substringAfter("object LibraryBackupZip {").substringBefore("fun restore(")
        val entries = Regex("""putNextEntry\(ZipEntry\(([^)]*)\)\)""").findAll(builder).map { it.groupValues[1] }.toList()
        assertEquals(listOf("InternalDatabase.DB_NAME", "\"shared_prefs/\$name.xml\""), entries)
        assertTrue(builder.contains("if (f.exists() && iad1tya.echo.music.backup.BackupExclusions.allows(\"shared_prefs/\$name.xml\")) {"))
        val body = code(builder)
        for (word in listOf("Cookie", "VisitorData", "DataSyncId", "license", "License", "SETTINGS_FILENAME", "dataStore", "noBackupFilesDir")) {
            assertFalse("the backup builder touches $word", body.contains(word))
        }
        assertTrue("one builder for the local, cloud and automatic copies", viewModel.contains("            LibraryBackupZip.write(context, database, uri)\n"))
    }

    @Test
    fun `every whitelisted file and the database pass the exclusion list, and the licence and session do not`() {
        val whitelist = viewModel.substringAfter("private val EQ_APPEARANCE_PREFS = listOf(").substringBefore(")")
        val names = Regex("\"([^\"]+)\"").findAll(whitelist).map { it.groupValues[1] }.toList()
        assertEquals(listOf("nanosonic_eq_profiles", "echo_eq_prefs", "eq_device_profiles", "echomusic_settings"), names)
        for (name in names) assertTrue(name, BackupExclusions.allows("shared_prefs/$name.xml"))
        assertTrue(BackupExclusions.allows("song.db"))
        assertTrue(main("db/MusicDatabase.kt").contains("const val DB_NAME = \"song.db\""))
        assertTrue(main("license/LicenseManager.kt").contains("private const val PREFS = \"jr_license\""))
        assertFalse(BackupExclusions.allows("shared_prefs/jr_license.xml"))
        assertTrue(viewModel.contains("const val SETTINGS_FILENAME = \"settings.preferences_pb\""))
        assertFalse(BackupExclusions.allows("settings.preferences_pb"))
    }

    @Test
    fun `the worker reuses the builder, runs only through WorkManager with its constraints, and skips a restore`() {
        val worker = main("backup/AutoBackupWorker.kt")
        assertTrue(worker.contains("BackupRestoreViewModel.LibraryBackupZip.write(ctx, MusicDatabaseEntryPoint.get(ctx), file.uri)"))
        assertTrue(worker.contains(".setRequiresBatteryNotLow(true)"))
        assertTrue(worker.contains(".setRequiresStorageNotLow(true)"))
        assertTrue(worker.contains("PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)"))
        assertTrue(worker.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertFalse("no loop, no polling", Regex("""\bwhile\s*\(|\bdelay\(""").containsMatchIn(code(worker)))
        assertTrue("off unless the user turned it on", worker.contains("if (prefs[AutoBackupEnabledKey] != true) return@withContext Result.success()"))
        assertTrue("never during a restore (row 19)", worker.contains("if (CrashHandler.isRestoring) return@withContext Result.success()"))
        assertTrue("only its own files are rotated", worker.contains("AutoBackupPolicy.toDelete(files.mapNotNull { it.name })"))
        // Keys are new and never renamed.
        for (key in listOf("auto_backup_enabled", "auto_backup_frequency", "auto_backup_folder", "auto_backup_last_ok", "auto_backup_last_failed")) {
            assertTrue(key, worker.contains("PreferencesKey(\"$key\")"))
        }
    }

    @Test
    fun `the setting is off by default and a copy restores through the unchanged local restore`() {
        val screen = main("ui/screens/settings/BackupAndRestore.kt")
        assertTrue(screen.contains("rememberPreference(iad1tya.echo.music.backup.AutoBackupEnabledKey, false)"))
        assertTrue(screen.contains("iad1tya.echo.music.backup.AutoBackupWorker.schedule(context, enabled, frequency)"))
        assertTrue(screen.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue("the restore path is the existing one", screen.contains("                viewModel.restore(context, uri)\n"))
        assertTrue(screen.contains("restoreLauncher.launch(arrayOf(\"*/*\"))"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.auto_backup_title), stringResource(R.string.backup_restore), \"settings/backup_restore\"),"))
    }

    @Test
    fun `a device without a folder picker gets a message, not a crash`() {
        val screen = main("ui/screens/settings/BackupAndRestore.kt")
        assertEquals("the picker opens only through the guarded helper", 1, Regex("""autoBackupFolderLauncher\.launch\(""").findAll(screen).count())
        val helper = screen.substringAfter("fun pickAutoBackupFolder(enableAfterPick: Boolean) {").substringBefore("\n    }\n")
        assertTrue(helper.contains("autoBackupFolderLauncher.launch(null)"))
        assertTrue(helper.contains("} catch (_: android.content.ActivityNotFoundException) {"))
        assertTrue(helper.contains("autoBackupEnableAfterPick = false"))
        assertTrue(helper.contains("R.string.export_directory_picker_unavailable"))
        assertEquals(3, Regex("""pickAutoBackupFolder\(enableAfterPick = (true|false)\)""").findAll(screen).count())
    }
}
