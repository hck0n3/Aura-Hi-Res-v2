package iad1tya.echo.music.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nobody may be left without a way to reach the current database version.
 *
 * This database is on version 42 with 42 exported schemas, 42 declared auto-migrations and 8 hand-written
 * ones — and until now NOTHING checked any of it. A missing or misfiled migration does not fail the build
 * and does not fail a test: it crashes Room at `open()`, on the phone, of someone who already had data,
 * in a loop, with no way out but wiping the app. It is the single most expensive failure this project can
 * ship, and the registry shows it has shipped TWICE already — 0.6.117 and again later — both times for
 * the same reason: the migration was registered on `MusicDatabase.newInstance`, a builder nothing uses,
 * instead of on `AppModule.provideInternalDatabase`, the one Hilt actually injects. The code says so
 * itself: *"every 38->39 update crashed with 'migration not found'"*.
 *
 * ## What this test checks, and what it does not
 * It reads the declarations from source and answers three questions that no compiler asks:
 *
 *  1. **Can every installed version still reach the current one?** Every version from 1 to the current one
 *     must have a path of migrations leading to it (an auto-migration, a manual one, or a chain of them —
 *     a manual migration that spans several versions, like 21→24, counts as one hop).
 *  2. **Is every hand-written migration actually plugged into the builder Hilt injects?** Declaring
 *     `MIGRATION_39_40` and forgetting to pass it to `addMigrations` is precisely the bug that shipped
 *     twice, and it looks completely fine in review.
 *  3. **Do the two builders agree?** `MusicDatabase.newInstance` keeps its own `addMigrations` list. When
 *     the two drift, the dead one reads as proof that a migration is registered when it is not — which is
 *     exactly how the second occurrence happened.
 *
 * It does NOT execute the migrations: that needs a real SQLite database and Room's generated code, which a
 * JVM test cannot reach. So this catches "there is no migration" and "the migration is not plugged in",
 * not "the migration's SQL is wrong". Executing them is the next step and this test does not pretend to
 * replace it.
 *
 * Source is parsed rather than reflected on purpose: the thing that broke is a *call site* in a Hilt
 * module, and no runtime reflection can see whether `addMigrations` was called with a given object without
 * building the database on a device. Parsing fails loudly and deterministically if the file moves.
 */
class MigrationCoverageTest {
    private val repoRoot: File by lazy {
        // Gradle runs unit tests with the working directory set to the module, but that is a default, not
        // a contract — walk up until the settings file that only the repo root has.
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(relative: String): String {
        val file = File(repoRoot, relative)
        assertTrue("$relative has moved — update this test to follow it", file.isFile)
        // Drop line comments so prose about a migration can never be mistaken for a declaration.
        return file.readLines().joinToString("\n") { it.substringBefore("//") }
    }

    private val databaseSource by lazy { source("app/src/main/kotlin/com/music/echo/db/MusicDatabase.kt") }
    private val appModuleSource by lazy { source("app/src/main/kotlin/com/music/echo/di/AppModule.kt") }

    /** The `version = N` of the `@Database` annotation. */
    private val currentVersion: Int by lazy {
        Regex("""version\s*=\s*(\d+)""").find(databaseSource)?.groupValues?.get(1)?.toInt()
            ?: error("could not read the @Database version")
    }

    /** `AutoMigration(from = A, to = B)` pairs. */
    private val autoMigrations: List<Pair<Int, Int>> by lazy {
        Regex("""AutoMigration\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)""")
            .findAll(databaseSource)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toList()
    }

    /** `val MIGRATION_A_B` declarations, by name. */
    private val declaredManualMigrations: Map<String, Pair<Int, Int>> by lazy {
        Regex("""val\s+(MIGRATION_(\d+)_(\d+))\b""")
            .findAll(databaseSource)
            .associate { m -> m.groupValues[1] to (m.groupValues[2].toInt() to m.groupValues[3].toInt()) }
    }

    /** Migration names passed to `addMigrations(...)` in the given source. */
    private fun registeredIn(source: String): Set<String> =
        Regex("""addMigrations\s*\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(source)
            .flatMap { block -> Regex("""MIGRATION_\d+_\d+""").findAll(block.groupValues[1]) }
            .map { it.value }
            .toSet()

    @Test
    fun `every version still installed can reach the current one`() {
        val edges = (autoMigrations + declaredManualMigrations.values)
            .groupBy({ it.first }, { it.second })

        // Walk backwards from the current version: anything that can reach a reachable version is itself
        // reachable. Cheaper and clearer than a search per version, and it names the first gap.
        val reachable = mutableSetOf(currentVersion)
        var changed = true
        while (changed) {
            changed = false
            edges.forEach { (from, targets) ->
                if (from !in reachable && targets.any { it in reachable }) {
                    reachable += from
                    changed = true
                }
            }
        }

        val stranded = (1 until currentVersion).filter { it !in reachable }
        assertTrue(
            "no migration path reaches v$currentVersion from version(s) $stranded — anyone still on one of " +
                "those crashes at startup with \"A migration from N to M was required but not found\", and " +
                "the only way out is wiping their data",
            stranded.isEmpty(),
        )
    }

    @Test
    fun `every hand-written migration is plugged into the builder Hilt injects`() {
        val registered = registeredIn(appModuleSource)
        val missing = declaredManualMigrations.keys - registered

        assertTrue(
            "$missing is declared in MusicDatabase.kt but never passed to addMigrations() in " +
                "AppModule.provideInternalDatabase — writing the migration is not what makes it run, and " +
                "this exact mistake shipped twice (see the comments on MIGRATION_37_38 / MIGRATION_38_39)",
            missing.isEmpty(),
        )
    }

    @Test
    fun `both builders register the same migrations`() {
        // MusicDatabase.newInstance has its own addMigrations list. It is not the builder Hilt uses, so
        // when the two drift it becomes a decoy: the migration looks registered and is not.
        val hilt = registeredIn(appModuleSource)
        val newInstance = registeredIn(databaseSource)

        assertEquals(
            "the two database builders no longer register the same migrations. Whichever one is missing an " +
                "entry, the disagreement itself is the hazard: one of them is a decoy that makes a missing " +
                "registration look fine in review",
            hilt.sorted(),
            newInstance.sorted(),
        )
    }

    @Test
    fun `every version has its exported schema`() {
        val dir = File(repoRoot, "app/schemas/iad1tya.echo.music.db.InternalDatabase")
        assertTrue("the exported schema directory has moved: ${dir.path}", dir.isDirectory)

        val exported = dir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .toSet()

        val missing = (1..currentVersion).filter { it !in exported }
        assertTrue(
            "no exported schema for version(s) $missing. Room validates a migrated database against these " +
                "files; without one, that step is skipped and a migration that leaves the schema subtly " +
                "wrong goes unnoticed until a query fails on a user's phone",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the schema for the current version exists and matches the declared version`() {
        // A version bump whose schema was never regenerated is the other half of the same failure: Room
        // compares identity hashes at open() and refuses a database it cannot recognise.
        val schema = File(repoRoot, "app/schemas/iad1tya.echo.music.db.InternalDatabase/$currentVersion.json")
        assertTrue(
            "the @Database version is $currentVersion but $currentVersion.json was never exported — build " +
                "once with exportSchema on and commit the file",
            schema.isFile,
        )
        assertTrue(
            "$currentVersion.json does not declare version $currentVersion",
            Regex(""""version"\s*:\s*$currentVersion\b""").containsMatchIn(schema.readText()),
        )
    }
}
