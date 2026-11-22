package iad1tya.echo.music.viewmodels

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for docs/REGRESSION_REGISTRY.md rows 11, 15 and 19 (BackupRestoreViewModel.kt, a shared file that
 * had no guard test). A restore that goes wrong loses the user's library with no way back, so the safety
 * steps are pinned here: if one is removed, this test fails even when the app still compiles.
 */
class BackupRestoreRegistryGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val source: String by lazy {
        val f = File(root, "app/src/main/kotlin/com/music/echo/viewmodels/BackupRestoreViewModel.kt")
        assertTrue("BackupRestoreViewModel.kt se movió: actualiza este test", f.isFile)
        f.readText()
    }

    private val backup get() = source.substringAfter("fun backup(").substringBefore("fun restore(")

    private val restore get() = source.substringAfter("fun restore(").substringBefore("private fun dbFileIntegrityOk(")

    @Test
    fun `row 15 - the backup never writes the settings file (cookie, tokens, init guards)`() {
        assertFalse("la copia no debe incluir settings.preferences_pb", backup.contains("ZipEntry(SETTINGS_FILENAME"))
        assertFalse(backup.contains("ZipEntry(\"settings.preferences_pb\""))
        assertTrue("la copia sigue guardando la base de datos", backup.contains("ZipEntry(InternalDatabase.DB_NAME)"))
    }

    @Test
    fun `row 15 - the backup whitelist of preferences never includes the license`() {
        val whitelist = source.substringAfter("private val EQ_APPEARANCE_PREFS = listOf(").substringBefore(")")
        assertFalse("la licencia nunca entra en una copia", whitelist.contains("license", ignoreCase = true))
    }

    @Test
    fun `row 15 - the restore never applies a settings file from an old backup`() {
        val applyPrefs = restore.substringAfter("// ---- 3b.").substringBefore("cleanupRestoreTemps(")
        assertFalse("restaurar no debe escribir el settings de otra copia", applyPrefs.contains("settingsTemp"))
    }

    @Test
    fun `row 11 - the backup DB is validated before it replaces the live one`() {
        val validate = restore.indexOf("validateAndPrepareRestoredDb(")
        val close = restore.indexOf("database.close()")
        assertTrue("se valida la copia", validate >= 0)
        assertTrue("se valida ANTES de cerrar la base de datos viva", close > validate)
        assertTrue(source.contains("PRAGMA integrity_check"))
    }

    @Test
    fun `row 11 - a failed copy rolls back to the previous DB and always restarts`() {
        assertTrue("se guarda la base de datos actual para poder volver atrás", restore.contains("dbFile.copyTo(bakFile, overwrite = true)"))
        assertTrue("se comprueba la copia recién escrita", restore.contains("if (!dbFileIntegrityOk(dbFile))"))
        val rollback = restore.substringAfter("} catch (copyError: Exception) {").substringBefore("// ---- 3b.")
        assertTrue("el fallo restaura la base de datos anterior", rollback.contains("bakFile.copyTo(dbFile, overwrite = true)"))
        assertTrue("el fallo reinicia la app", rollback.contains("restartApp(context)"))
        assertTrue("restartApp termina el proceso", source.substringAfter("private fun restartApp(").contains("exitProcess(0)"))
    }

    @Test
    fun `row 19 - the crash handler is told about the restore before the DB is closed`() {
        val flag = restore.indexOf("CrashHandler.isRestoring = true")
        val closed = restore.indexOf("dbClosedForRestore = true")
        val close = restore.indexOf("database.close()")
        assertTrue("se avisa al CrashHandler", flag >= 0)
        assertTrue("el aviso va antes de cerrar", close > flag)
        assertTrue("se marca como cerrada antes de cerrar", closed in 0 until close)
        val crash = File(root, "app/src/main/kotlin/com/music/echo/utils/CrashHandler.kt").readText()
        assertTrue("el CrashHandler solo perdona el cierre durante una restauración", crash.contains("isRestoring && throwable.isConnectionClosed()"))
    }

    /** Row 19 (2026-11-22): a cloud restore on 2.0.68 still crashed — the closed DB came in shapes the guard missed. */
    @Test
    fun `row 19 - both closed-DB predicates recognise every shape of the restore race`() {
        for (path in listOf("app/src/main/kotlin/com/music/echo/utils/CrashHandler.kt", "app/src/main/kotlin/com/music/echo/db/MusicDatabase.kt")) {
            val src = File(root, path).readText()
            assertTrue("$path: code 21 in the cause chain", src.contains("m.contains(\"connection is closed\") || m.contains(\"error code: 21\")"))
            assertTrue("$path: SQLite's re-open of a closed database", src.contains("re-open an already-closed object: sqlitedatabase"))
            assertTrue("$path: Room's throwIfClosed", src.contains("m.contains(\"database is closed\")"))
            assertTrue("$path: code 21 attached as a suppressed exception", src.contains("t.suppressed.any"))
        }
    }
}
