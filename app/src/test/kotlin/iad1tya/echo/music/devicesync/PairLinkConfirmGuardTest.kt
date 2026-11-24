package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 836 (owner 2026-11-24, «haz K1, K2 y K3»): an opened pairing link (`echomusic://pair?code=…`, row 615) asks before it
 * changes this device's sync room, like the Listen Together invite of row 804; and the example in the code field is not a code.
 */
class PairLinkConfirmGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val activity = "app/src/main/kotlin/com/music/echo/MainActivity.kt"

    @Test
    fun `an opened pairing link only remembers the code and the dialog saves it on Unirme`() {
        val main = read(activity)
        val branch = main.substringAfter("if (uri.scheme == \"echomusic\" && uri.host.equals(\"pair\", ignoreCase = true)) {")
            .substringBefore("\n            return\n        }")
        assertTrue(branch.contains("val normalized = iad1tya.echo.music.devicesync.SyncCode.fromPairLink(uri.toString())"))
        assertTrue(branch.contains("pendingPairCode = normalized"))
        assertFalse("the link never saves the code by itself", branch.contains("DeviceSyncCodeKey"))
        assertTrue("an invalid link is still told", branch.contains("R.string.qr_pair_invalid"))

        val dialog = main.substringAfter("pendingPairCode?.let { code ->").substringBefore("dismissButton = {")
        assertTrue(dialog.contains("title = { Text(stringResource(R.string.qr_pair_confirm_title)) },"))
        val tap = dialog.indexOf("ActionLog.tap(ActionId.MAIN_PAIR_JOIN)")
        val save = dialog.indexOf("this@MainActivity.dataStore.edit { it[iad1tya.echo.music.devicesync.DeviceSyncCodeKey] = code }")
        assertTrue("only «Unirme» saves the code", tap >= 0 && save > tap)
        assertTrue(dialog.contains("onDismissRequest = { pendingPairCode = null },"))
        assertTrue(main.contains("ActionLog.tap(ActionId.MAIN_PAIR_CANCEL); pendingPairCode = null"))
        // The unanswered question survives a rotation (the link's data is consumed when it is read).
        assertTrue(main.contains("pendingPairCode?.let { outState.putString(STATE_PENDING_PAIR_CODE, it) }"))
        assertTrue(main.contains("pendingPairCode = savedInstanceState?.getString(STATE_PENDING_PAIR_CODE)"))
        // Row 804 stays as it was.
        assertTrue(main.contains("pendingListenInvite = listenCode"))
    }

    @Test
    fun `the dialog texts exist in English and both Spanish sets`() {
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            val qr = read("app/src/main/res/$dir/qr_strings.xml")
            assertTrue(dir, qr.contains("<string name=\"qr_pair_confirm_title\">"))
            assertTrue(dir, qr.contains("<string name=\"qr_pair_confirm_join\">"))
        }
        assertTrue(read("app/src/main/res/values-es/qr_strings.xml").contains("¿Emparejar este aparato con otra sala de sincronización?"))
    }

    @Test
    fun `the example in the pairing code field is not a valid code`() {
        val pattern = Regex("<string name=\"device_sync_code_dialog_hint\">([^<]*)</string>")
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            val hint = pattern.find(read("app/src/main/res/$dir/echo_strings.xml"))?.groupValues?.get(1)
            assertTrue("$dir: hint missing", hint != null)
            assertNull("$dir: «$hint» would pair if typed as it is", SyncCode.normalize(hint!!))
        }
        // The old example was a full code of the alphabet.
        assertEquals("AAAABBBBCCCCDDDDEEEE", SyncCode.normalize("AAAA-BBBB-CCCC-DDDD-EEEE"))
    }
}
