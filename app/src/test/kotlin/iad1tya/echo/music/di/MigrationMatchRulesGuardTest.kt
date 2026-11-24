package iad1tya.echo.music.di

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 843 — the migration's match cache returns a hit before RecordingMatcher runs, so automatic matches cached by an
 * older build are dropped once when the cache database opens. Only automatic ones: a match the user picked is never deleted.
 */
class MigrationMatchRulesGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(relative: String): String {
        val file = File(repoRoot, relative)
        assertTrue("$relative has moved — update this test to follow it", file.isFile)
        return file.readLines().joinToString("\n") { it.substringBefore("//") }
    }

    private val module by lazy { source("app/src/main/kotlin/com/music/echo/di/MigrationModule.kt") }
    private val matchCache by lazy { source("migration/src/main/kotlin/com/aura/migration/db/MatchCache.kt") }

    private val clearAutomatic = "DELETE FROM match_cache WHERE verifiedByUser = 0"

    @Test
    fun `the cache database drops automatic matches once when it opens`() {
        val builder = module.substringAfter("fun provideMigrationCacheDatabase(").substringBefore(".build()")
        assertTrue("the callback must be on the match cache database builder", builder.contains("override fun onOpen("))
        assertTrue(builder.contains("db.execSQL(\"$clearAutomatic\")"))
        assertTrue("the delete runs once, behind a version mark", builder.contains("MATCH_RULES_VERSION_KEY"))
    }

    @Test
    fun `only automatic matches are deleted, never the user's own picks`() {
        assertTrue("the DAO's clearAutomatic query changed", matchCache.contains("\"$clearAutomatic\""))
        assertEquals(
            "every delete on match_cache must keep verifiedByUser rows",
            Regex("DELETE FROM match_cache").findAll(module).count(),
            Regex(Regex.escape(clearAutomatic)).findAll(module).count(),
        )
    }
}
