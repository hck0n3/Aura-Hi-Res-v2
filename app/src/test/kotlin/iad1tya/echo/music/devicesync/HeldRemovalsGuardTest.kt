package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 807 (owner 2026-11-24, H10): the removals the library sync's mass-deletion brake holds wait for the user's «Aplicar» /
 * «Descartar». The behaviour is pinned by `LibrarySyncSimulationTest`; this pins the wiring that makes it reachable: the notice with
 * «Revisar», the row in Ajustes ▸ Sincronizar dispositivos, the screen and its two buttons, and that the counts carry no titles.
 */
class HeldRemovalsGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val main = "app/src/main/kotlin/com/music/echo"

    @Test
    fun `the brake tells the app once per batch, and the log carries counts only`() {
        val sync = read("$main/devicesync/LibrarySync.kt")
        assertTrue(sync.contains("DeviceSyncHub.heldTrip.value = DeviceSyncHub.HeldTrip((DeviceSyncHub.heldTrip.value?.token ?: 0) + 1, held.size)"))
        assertTrue(sync.contains("groupingBy { it.key.substringBefore(':') }.eachCount()"))
        assertTrue(sync.contains("Timber.tag(TAG).i(\"LIB_SYNC held removals resolved apply=%b count=%d\", apply, held.size)"))
        assertTrue("Descartar sends the copies back through the usual diff", sync.contains("if (!apply && connected && pulledOnce && isEnabled()) pushDiffLocked()"))
        assertTrue("Aplicar goes through the same adapter", sync.contains("adapter.apply(LibrarySyncCore.plan(released, before, LibrarySyncCore.PlaylistIds.of(ledger, released)))"))
    }

    @Test
    fun `the notice offers Revisar and opens the screen`() {
        val activity = read("$main/MainActivity.kt")
        val block = activity.substringAfter("DeviceSyncHub.heldTrip.collectLatest { trip ->").substringBefore("\n                }\n")
        assertTrue(block.contains("if (trip == null) return@collectLatest"))
        assertTrue(block.contains("actionLabel = heldReview"))
        val navigate = block.indexOf("navController.navigate(\"settings/device_sync/held\")")
        assertTrue(navigate >= 0)
        assertTrue("answered once: cleared after the notice, not before", navigate < block.indexOf("DeviceSyncHub.heldTrip.compareAndSet(trip, null)"))
    }

    @Test
    fun `Ajustes has the row, the route exists and the screen answers with both buttons`() {
        val settings = read("$main/ui/screens/settings/integrations/DeviceSyncSettings.kt")
        assertTrue(settings.contains("navController.navigate(\"settings/device_sync/held\")"))
        assertTrue(read("$main/ui/screens/NavigationBuilder.kt").contains("composable(route = \"settings/device_sync/held\")"))
        val screen = read("$main/ui/screens/settings/integrations/HeldRemovalsScreen.kt")
        assertTrue(screen.contains("DeviceSyncHub.controller?.resolveHeldRemovals(apply = true)"))
        assertTrue(screen.contains("DeviceSyncHub.controller?.resolveHeldRemovals(apply = false)"))
        assertFalse("counts only, never which items", screen.contains("ledger"))
        assertTrue(read("$main/ui/screens/settings/SearchableSettings.kt").contains("R.string.device_sync_held_title"))
    }

    @Test
    fun `the texts exist in English and Spanish`() {
        for (dir in listOf("values", "values-es")) {
            val xml = read("app/src/main/res/$dir/device_sync_held_strings.xml")
            for (name in listOf("device_sync_held_title", "device_sync_held_notice", "device_sync_held_review", "device_sync_held_apply", "device_sync_held_discard")) {
                assertTrue("$dir: $name", xml.contains("name=\"$name\""))
            }
            assertTrue(dir, xml.contains("name=\"device_sync_held_notice\">") && xml.contains("%1\$d"))
        }
    }
}
