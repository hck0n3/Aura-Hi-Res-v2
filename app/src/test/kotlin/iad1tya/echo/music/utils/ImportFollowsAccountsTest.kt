package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.ImportFollowsAccounts.Lists
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 808 (owner 2026-11-24, H13): «esperan confirmación» and «Solo en Aura» belong to the Google account they were answered
 * for. A switch of account never releases a held artist (registry #646: an import never subscribes an account by itself).
 */
class ImportFollowsAccountsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `an account seen for the first time waits for its own answer on everything the other one held`() {
        val a = Lists(awaiting = setOf("x"), localOnly = setOf("y"))
        assertEquals(Lists(awaiting = setOf("x", "y"), localOnly = emptySet()), ImportFollowsAccounts.incoming(a, stored = null))
    }

    @Test
    fun `an account seen before gets its own answers back, and what was imported meanwhile waits for it`() {
        val stored = Lists(awaiting = setOf("w"), localOnly = setOf("y"))
        // On the other account: y was answered «Suscribir» there (gone from its lists), z was imported there.
        val outgoing = Lists(awaiting = setOf("z"), localOnly = setOf("y2"))
        val next = ImportFollowsAccounts.incoming(outgoing, stored)
        assertEquals("its «Solo en Aura» is its own", setOf("y"), next.localOnly)
        assertEquals("its own waiting ones, plus what the other account still held", setOf("w", "z", "y2"), next.awaiting)
    }

    @Test
    fun `a switch never holds fewer artists than before it`() {
        val outgoing = Lists(awaiting = setOf("a", "b"), localOnly = setOf("c"))
        for (stored in listOf(null, Lists(emptySet(), emptySet()), Lists(setOf("d"), setOf("a")), Lists(emptySet(), setOf("c")))) {
            val next = ImportFollowsAccounts.incoming(outgoing, stored)
            assertTrue("$stored", (next.awaiting + next.localOnly).containsAll(outgoing.awaiting + outgoing.localOnly))
            assertTrue("$stored", next.awaiting.intersect(next.localOnly).isEmpty())
        }
    }

    @Test
    fun `the account is told apart by a hash of its email, never the email`() {
        val tag = ImportFollowsAccounts.tagOf("Someone@Example.com ")
        assertEquals(tag, ImportFollowsAccounts.tagOf("someone@example.com"))
        assertNotEquals(tag, ImportFollowsAccounts.tagOf("other@example.com"))
        assertEquals(12, tag!!.length)
        assertFalse(tag.contains("someone"))
        assertNull(ImportFollowsAccounts.tagOf(""))
        assertNull(ImportFollowsAccounts.tagOf(null))
    }

    @Test
    fun `the upload pass adopts the signed-in account's lists before it reads them, and the keys keep their names`() {
        val upload = main("utils/LibraryUploadSync.kt")
        val held = upload.substringAfter("suspend fun importFollowsHeldBack(): Set<String>? = withContext(Dispatchers.IO) {")
        val adopt = held.indexOf("ImportFollowsAccounts.adopt(context)")
        assertTrue(adopt >= 0)
        assertTrue(adopt < held.indexOf("context.dataStore.data.first()"))
        val keys = main("constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val ImportFollowsAwaitingConfirmKey = stringSetPreferencesKey(\"import_follows_awaiting_confirm\")"))
        assertTrue(keys.contains("val ImportFollowsLocalOnlyKey = stringSetPreferencesKey(\"import_follows_local_only\")"))
    }

    @Test
    fun `the switch logs counts only and first-time lists stay where they are`() {
        val src = main("utils/ImportFollowsAccounts.kt")
        val adopt = src.substringAfter("suspend fun adopt(context: Context) {")
        assertTrue(adopt.contains("Timber.i(\"IMPORT_FOLLOWS account switched seen=%b awaiting=%d localOnly=%d\", seen, next.awaiting.size, next.localOnly.size)"))
        assertFalse("the email and its tag never reach the log", Regex("""Timber\.[a-z]\([^\n]*(tag|owner|Email)""").containsMatchIn(adopt))
        val firstTime = adopt.substringAfter("if (owner == null) {").substringBefore("}")
        assertTrue(firstTime.contains("p[OwnerKey] = tag"))
        assertFalse("the one-time migration moves nothing", firstTime.contains("ImportFollowsAwaitingConfirmKey"))
    }
}
